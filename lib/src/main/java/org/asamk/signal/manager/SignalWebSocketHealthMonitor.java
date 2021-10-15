package org.asamk.signal.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Monitors the health of the identified and unidentified WebSockets. If either one appears to be
 * unhealthy, will trigger restarting both.
 * <p>
 * The monitor is also responsible for sending heartbeats/keep-alive messages to prevent
 * timeouts.
 */
public final class SignalWebSocketHealthMonitor implements HealthMonitor {

    private final static Logger logger = LoggerFactory.getLogger(SignalWebSocketHealthMonitor.class);

    private static final long KEEP_ALIVE_SEND_CADENCE = TimeUnit.SECONDS.toMillis(WebSocketConnection.KEEPALIVE_TIMEOUT_SECONDS);
    private static final long MAX_TIME_SINCE_SUCCESSFUL_KEEP_ALIVE = KEEP_ALIVE_SEND_CADENCE * 3;

    private SignalWebSocket signalWebSocket;
    private final SleepTimer sleepTimer;

    private volatile KeepAliveSender keepAliveSender;

    private final HealthState identified = new HealthState();
    private final HealthState unidentified = new HealthState();

    public SignalWebSocketHealthMonitor(SleepTimer sleepTimer) {
        this.sleepTimer = sleepTimer;
    }

    public void monitor(SignalWebSocket signalWebSocket) {
        Preconditions.checkNotNull(signalWebSocket);
        Preconditions.checkArgument(this.signalWebSocket == null, "monitor can only be called once");

        this.signalWebSocket = signalWebSocket;

        //noinspection ResultOfMethodCallIgnored
        signalWebSocket.getWebSocketState()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .distinctUntilChanged()
                .subscribe(s -> onStateChange(s, identified));

        //noinspection ResultOfMethodCallIgnored
        signalWebSocket.getUnidentifiedWebSocketState()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .distinctUntilChanged()
                .subscribe(s -> onStateChange(s, unidentified));
    }

    private synchronized void onStateChange(WebSocketConnectionState connectionState, HealthState healthState) {
        switch (connectionState) {
            case CONNECTED:
                logger.debug("WebSocket is now connected");
                break;
            case AUTHENTICATION_FAILED:
                logger.debug("WebSocket authentication failed");
                break;
            case FAILED:
                logger.debug("WebSocket connection failed");
                break;
        }

        healthState.needsKeepAlive = connectionState == WebSocketConnectionState.CONNECTED;

        if (keepAliveSender == null && isKeepAliveNecessary()) {
            keepAliveSender = new KeepAliveSender();
            keepAliveSender.start();
        } else if (keepAliveSender != null && !isKeepAliveNecessary()) {
            keepAliveSender.shutdown();
            keepAliveSender = null;
        }
    }

    @Override
    public void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket) {
        if (isIdentifiedWebSocket) {
            identified.lastKeepAliveReceived = System.currentTimeMillis();
        } else {
            unidentified.lastKeepAliveReceived = System.currentTimeMillis();
        }
    }

    @Override
    public void onMessageError(int status, boolean isIdentifiedWebSocket) {
        if (status == 409) {
            HealthState healthState = (isIdentifiedWebSocket ? identified : unidentified);
            if (healthState.mismatchErrorTracker.addSample(System.currentTimeMillis())) {
                logger.warn("Received too many mismatch device errors, forcing new websockets.");
                signalWebSocket.forceNewWebSockets();
                signalWebSocket.connect();
            }
        }
    }

    private boolean isKeepAliveNecessary() {
        return identified.needsKeepAlive || unidentified.needsKeepAlive;
    }

    private static class HealthState {

        private final HttpErrorTracker mismatchErrorTracker = new HttpErrorTracker(5, TimeUnit.MINUTES.toMillis(1));

        private volatile boolean needsKeepAlive;
        private volatile long lastKeepAliveReceived;
    }

    /**
     * Sends periodic heartbeats/keep-alives over both WebSockets to prevent connection timeouts. If
     * either WebSocket fails 3 times to get a return heartbeat both are forced to be recreated.
     */
    private class KeepAliveSender extends Thread {

        private volatile boolean shouldKeepRunning = true;

        public void run() {
            identified.lastKeepAliveReceived = System.currentTimeMillis();
            unidentified.lastKeepAliveReceived = System.currentTimeMillis();

            while (shouldKeepRunning && isKeepAliveNecessary()) {
                try {
                    sleepTimer.sleep(KEEP_ALIVE_SEND_CADENCE);

                    if (shouldKeepRunning && isKeepAliveNecessary()) {
                        long keepAliveRequiredSinceTime = System.currentTimeMillis()
                                - MAX_TIME_SINCE_SUCCESSFUL_KEEP_ALIVE;

                        if (identified.lastKeepAliveReceived < keepAliveRequiredSinceTime
                                || unidentified.lastKeepAliveReceived < keepAliveRequiredSinceTime) {
                            logger.warn("Missed keep alives, identified last: "
                                    + identified.lastKeepAliveReceived
                                    + " unidentified last: "
                                    + unidentified.lastKeepAliveReceived
                                    + " needed by: "
                                    + keepAliveRequiredSinceTime);
                            signalWebSocket.forceNewWebSockets();
                            signalWebSocket.connect();
                        } else {
                            signalWebSocket.sendKeepAlive();
                        }
                    }
                } catch (Throwable e) {
                    logger.warn("Error occured in KeepAliveSender, ignoring ...", e);
                }
            }
        }

        public void shutdown() {
            shouldKeepRunning = false;
        }
    }

    private final static class HttpErrorTracker {

        private final long[] timestamps;
        private final long errorTimeRange;

        public HttpErrorTracker(int samples, long errorTimeRange) {
            this.timestamps = new long[samples];
            this.errorTimeRange = errorTimeRange;
        }

        public synchronized boolean addSample(long now) {
            long errorsMustBeAfter = now - errorTimeRange;
            int count = 1;
            int minIndex = 0;

            for (int i = 0; i < timestamps.length; i++) {
                if (timestamps[i] < errorsMustBeAfter) {
                    timestamps[i] = 0;
                } else if (timestamps[i] != 0) {
                    count++;
                }

                if (timestamps[i] < timestamps[minIndex]) {
                    minIndex = i;
                }
            }

            timestamps[minIndex] = now;

            if (count >= timestamps.length) {
                Arrays.fill(timestamps, 0);
                return true;
            }
            return false;
        }
    }
}
