package org.asamk.signal.manager.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.api.websocket.SignalWebSocket;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;

final class SignalWebSocketHealthMonitor implements HealthMonitor {

    private static final Logger logger = LoggerFactory.getLogger(SignalWebSocketHealthMonitor.class);

    /**
     * This is the amount of time in between sent keep alives. Must be greater than [KEEP_ALIVE_TIMEOUT]
     */
    private static final long KEEP_ALIVE_SEND_CADENCE = TimeUnit.SECONDS.toMillis(OkHttpWebSocketConnection.KEEPALIVE_FREQUENCY_SECONDS);

    /**
     * This is the amount of time we will wait for a response to the keep alive before we consider the websockets dead.
     * It is required that this value be less than [KEEP_ALIVE_SEND_CADENCE]
     */
    private static final long KEEP_ALIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(20);

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final SleepTimer sleepTimer;
    private SignalWebSocket webSocket = null;
    private volatile KeepAliveSender keepAliveSender = null;
    private boolean needsKeepAlive = false;
    private long lastKeepAliveReceived = 0;

    public SignalWebSocketHealthMonitor(SleepTimer sleepTimer) {
        this.sleepTimer = sleepTimer;
    }

    void monitor(SignalWebSocket webSocket) {
        Preconditions.checkNotNull(webSocket);
        Preconditions.checkArgument(this.webSocket == null, "monitor can only be called once");

        executor.execute(() -> {

            this.webSocket = webSocket;

            webSocket.getState()
                    .subscribeOn(Schedulers.computation())
                    .observeOn(Schedulers.computation())
                    .distinctUntilChanged()
                    .subscribe(this::onStateChanged);

            webSocket.addKeepAliveChangeListener(() -> {
                executor.execute(this::updateKeepAliveSenderStatus);
                return Unit.INSTANCE;
            });
        });
    }

    private void onStateChanged(WebSocketConnectionState connectionState) {
        executor.execute(() -> {
            needsKeepAlive = connectionState == WebSocketConnectionState.CONNECTED;

            updateKeepAliveSenderStatus();
        });
    }

    @Override
    public void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket) {
        final var keepAliveTime = System.currentTimeMillis();
        executor.execute(() -> lastKeepAliveReceived = keepAliveTime);
    }

    @Override
    public void onMessageError(int status, boolean isIdentifiedWebSocket) {
    }

    private void updateKeepAliveSenderStatus() {
        if (keepAliveSender == null && sendKeepAlives()) {
            keepAliveSender = new KeepAliveSender();
            keepAliveSender.start();
        } else if (keepAliveSender != null && !sendKeepAlives()) {
            keepAliveSender.shutdown();
            keepAliveSender = null;
        }
    }

    private boolean sendKeepAlives() {
        return needsKeepAlive && webSocket != null && webSocket.shouldSendKeepAlives();
    }

    /**
     * Sends periodic heartbeats/keep-alives over the WebSocket to prevent connection timeouts. If
     * the WebSocket fails to get a return heartbeat after [KEEP_ALIVE_TIMEOUT] seconds, it is forced to be recreated.
     */
    private final class KeepAliveSender extends Thread {

        private volatile boolean shouldKeepRunning = true;

        @Override
        public void run() {
            logger.debug("[KeepAliveSender({})] started", this.threadId());
            lastKeepAliveReceived = System.currentTimeMillis();

            var keepAliveSendTime = System.currentTimeMillis();
            while (shouldKeepRunning && sendKeepAlives()) {
                try {
                    final var nextKeepAliveSendTime = keepAliveSendTime + KEEP_ALIVE_SEND_CADENCE;
                    sleepUntil(nextKeepAliveSendTime);

                    if (shouldKeepRunning && sendKeepAlives()) {
                        keepAliveSendTime = System.currentTimeMillis();
                        webSocket.sendKeepAlive();
                    }

                    final var responseRequiredTime = keepAliveSendTime + KEEP_ALIVE_TIMEOUT;
                    sleepUntil(responseRequiredTime);

                    if (shouldKeepRunning && sendKeepAlives()) {
                        if (lastKeepAliveReceived < keepAliveSendTime) {
                            logger.debug("Missed keep alive, last: {} needed by: {}",
                                    lastKeepAliveReceived,
                                    responseRequiredTime);
                            webSocket.forceNewWebSocket();
                        }
                    }
                } catch (Throwable e) {
                    logger.warn("Keep alive sender failed", e);
                }
            }
            logger.debug("[KeepAliveSender({})] ended", threadId());
        }

        void sleepUntil(long timeMillis) {
            while (System.currentTimeMillis() < timeMillis) {
                final var waitTime = timeMillis - System.currentTimeMillis();
                if (waitTime > 0) {
                    try {
                        sleepTimer.sleep(waitTime);
                    } catch (InterruptedException e) {
                        logger.warn("WebSocket health monitor interrupted", e);
                    }
                }
            }
        }

        void shutdown() {
            shouldKeepRunning = false;
        }
    }
}

