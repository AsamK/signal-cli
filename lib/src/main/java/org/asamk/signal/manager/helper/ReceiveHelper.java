package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.actions.HandleAction;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.UntrustedIdentityException;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.messageCache.CachedMessage;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ReceiveHelper {

    private final static Logger logger = LoggerFactory.getLogger(ReceiveHelper.class);
    private final static int MAX_BACKOFF_COUNTER = 9;

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final Context context;

    private ReceiveConfig receiveConfig = new ReceiveConfig(false, false, false);
    private boolean needsToRetryFailedMessages = false;
    private boolean hasCaughtUpWithOldMessages = false;
    private boolean isWaitingForMessage = false;
    private boolean shouldStop = false;
    private Callable authenticationFailureListener;
    private Callable caughtUpWithOldMessagesListener;

    public ReceiveHelper(final Context context) {
        this.account = context.getAccount();
        this.dependencies = context.getDependencies();
        this.context = context;
    }

    public void setReceiveConfig(final ReceiveConfig receiveConfig) {
        this.receiveConfig = receiveConfig;
        dependencies.setAllowStories(!receiveConfig.ignoreStories());
    }

    public void setNeedsToRetryFailedMessages(final boolean needsToRetryFailedMessages) {
        this.needsToRetryFailedMessages = needsToRetryFailedMessages;
    }

    public void setAuthenticationFailureListener(final Callable authenticationFailureListener) {
        this.authenticationFailureListener = authenticationFailureListener;
    }

    public void setCaughtUpWithOldMessagesListener(final Callable caughtUpWithOldMessagesListener) {
        this.caughtUpWithOldMessagesListener = caughtUpWithOldMessagesListener;
    }

    public boolean requestStopReceiveMessages() {
        this.shouldStop = true;
        return isWaitingForMessage;
    }

    public void receiveMessagesContinuously(Manager.ReceiveMessageHandler handler) {
        while (!shouldStop) {
            try {
                receiveMessages(Duration.ofMinutes(1), false, null, handler);
                break;
            } catch (IOException e) {
                logger.warn("Receiving messages failed, retrying", e);
            }
        }
    }

    public void receiveMessages(
            Duration timeout, boolean returnOnTimeout, Integer maxMessages, Manager.ReceiveMessageHandler handler
    ) throws IOException {
        needsToRetryFailedMessages = true;
        hasCaughtUpWithOldMessages = false;

        // Use a Map here because java Set doesn't have a get method ...
        Map<HandleAction, HandleAction> queuedActions = new HashMap<>();

        final var signalWebSocket = dependencies.getSignalWebSocket();
        final var webSocketStateDisposable = Observable.merge(signalWebSocket.getUnidentifiedWebSocketState(),
                        signalWebSocket.getWebSocketState())
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .distinctUntilChanged()
                .subscribe(this::onWebSocketStateChange);
        signalWebSocket.connect();

        try {
            receiveMessagesInternal(signalWebSocket, timeout, returnOnTimeout, maxMessages, handler, queuedActions);
        } finally {
            hasCaughtUpWithOldMessages = false;
            handleQueuedActions(queuedActions.keySet());
            queuedActions.clear();
            signalWebSocket.disconnect();
            webSocketStateDisposable.dispose();
            shouldStop = false;
        }
    }

    private void receiveMessagesInternal(
            final SignalWebSocket signalWebSocket,
            Duration timeout,
            boolean returnOnTimeout,
            Integer maxMessages,
            Manager.ReceiveMessageHandler handler,
            final Map<HandleAction, HandleAction> queuedActions
    ) throws IOException {
        int remainingMessages = maxMessages == null ? -1 : maxMessages;
        var backOffCounter = 0;
        isWaitingForMessage = false;

        while (!shouldStop && remainingMessages != 0) {
            if (needsToRetryFailedMessages) {
                retryFailedReceivedMessages(handler);
                needsToRetryFailedMessages = false;
            }
            SignalServiceEnvelope envelope;
            final CachedMessage[] cachedMessage = {null};
            final var nowMillis = System.currentTimeMillis();
            if (nowMillis - account.getLastReceiveTimestamp() > 60000) {
                account.setLastReceiveTimestamp(nowMillis);
            }
            logger.debug("Checking for new message from server");
            try {
                isWaitingForMessage = true;
                var queueNotEmpty = signalWebSocket.readMessageBatch(timeout.toMillis(), 1, batch -> {
                    logger.debug("Retrieved {} envelopes!", batch.size());
                    isWaitingForMessage = false;
                    for (final var it : batch) {
                        SignalServiceEnvelope envelope1 = new SignalServiceEnvelope(it.getEnvelope(),
                                it.getServerDeliveredTimestamp());
                        final var recipientId = envelope1.hasSourceServiceId() ? account.getRecipientResolver()
                                .resolveRecipient(envelope1.getSourceAddress()) : null;
                        logger.trace("Storing new message from {}", recipientId);
                        // store message on disk, before acknowledging receipt to the server
                        cachedMessage[0] = account.getMessageCache().cacheMessage(envelope1, recipientId);
                        try {
                            signalWebSocket.sendAck(it);
                        } catch (IOException e) {
                            logger.warn("Failed to ack envelope to server after storing it: {}", e.getMessage());
                        }
                    }
                });
                isWaitingForMessage = false;
                backOffCounter = 0;

                if (queueNotEmpty) {
                    if (remainingMessages > 0) {
                        remainingMessages -= 1;
                    }
                    envelope = cachedMessage[0].loadEnvelope();
                    logger.debug("New message received from server");
                } else {
                    logger.debug("Received indicator that server queue is empty");
                    handleQueuedActions(queuedActions.keySet());
                    queuedActions.clear();

                    hasCaughtUpWithOldMessages = true;
                    caughtUpWithOldMessagesListener.call();

                    // Continue to wait another timeout for new messages
                    continue;
                }
            } catch (AssertionError e) {
                if (e.getCause() instanceof InterruptedException) {
                    break;
                } else {
                    throw e;
                }
            } catch (IOException e) {
                logger.debug("Pipe unexpectedly unavailable: {}", e.getMessage());
                if (e instanceof WebSocketUnavailableException || "Connection closed!".equals(e.getMessage())) {
                    final var sleepMilliseconds = 100 * (long) Math.pow(2, backOffCounter);
                    backOffCounter = Math.min(backOffCounter + 1, MAX_BACKOFF_COUNTER);
                    logger.warn("Connection closed unexpectedly, reconnecting in {} ms", sleepMilliseconds);
                    try {
                        Thread.sleep(sleepMilliseconds);
                    } catch (InterruptedException interruptedException) {
                        return;
                    }
                    hasCaughtUpWithOldMessages = false;
                    signalWebSocket.connect();
                    continue;
                }
                throw e;
            } catch (TimeoutException e) {
                backOffCounter = 0;
                if (returnOnTimeout) return;
                continue;
            } catch (Exception e) {
                logger.error("Unknown error when receiving messages", e);
                continue;
            }

            try {
                final var result = context.getIncomingMessageHandler().handleEnvelope(envelope, receiveConfig, handler);
                for (final var h : result.first()) {
                    final var existingAction = queuedActions.get(h);
                    if (existingAction == null) {
                        queuedActions.put(h, h);
                    } else {
                        existingAction.mergeOther(h);
                    }
                }
                final var exception = result.second();

                if (hasCaughtUpWithOldMessages) {
                    handleQueuedActions(queuedActions.keySet());
                    queuedActions.clear();
                }
                if (cachedMessage[0] != null) {
                    if (exception instanceof UntrustedIdentityException) {
                        logger.debug("Keeping message with untrusted identity in message cache");
                        final var address = ((UntrustedIdentityException) exception).getSender();
                        if (!envelope.hasSourceServiceId() && address.uuid().isPresent()) {
                            final var recipientId = account.getRecipientResolver()
                                    .resolveRecipient(ACI.from(address.uuid().get()));
                            try {
                                cachedMessage[0] = account.getMessageCache()
                                        .replaceSender(cachedMessage[0], recipientId);
                            } catch (IOException ioException) {
                                logger.warn("Failed to move cached message to recipient folder: {}",
                                        ioException.getMessage(),
                                        ioException);
                            }
                        }
                    } else {
                        cachedMessage[0].delete();
                    }
                }
            } catch (Exception e) {
                logger.error("Unknown error when handling messages", e);
            }
        }
    }

    private void retryFailedReceivedMessages(Manager.ReceiveMessageHandler handler) {
        Set<HandleAction> queuedActions = new HashSet<>();
        for (var cachedMessage : account.getMessageCache().getCachedMessages()) {
            var actions = retryFailedReceivedMessage(handler, cachedMessage);
            if (actions != null) {
                queuedActions.addAll(actions);
            }
        }
        handleQueuedActions(queuedActions);
    }

    private List<HandleAction> retryFailedReceivedMessage(
            final Manager.ReceiveMessageHandler handler, final CachedMessage cachedMessage
    ) {
        var envelope = cachedMessage.loadEnvelope();
        if (envelope == null) {
            cachedMessage.delete();
            return null;
        }

        final var result = context.getIncomingMessageHandler().handleRetryEnvelope(envelope, receiveConfig, handler);
        final var actions = result.first();
        final var exception = result.second();

        if (exception instanceof UntrustedIdentityException) {
            if (System.currentTimeMillis() - envelope.getServerDeliveredTimestamp() > 1000L * 60 * 60 * 24 * 30) {
                // Envelope is more than a month old, cleaning up.
                cachedMessage.delete();
                return null;
            }
            if (!envelope.hasSourceServiceId()) {
                final var identifier = ((UntrustedIdentityException) exception).getSender();
                final var recipientId = account.getRecipientResolver()
                        .resolveRecipient(new RecipientAddress(identifier));
                try {
                    account.getMessageCache().replaceSender(cachedMessage, recipientId);
                } catch (IOException ioException) {
                    logger.warn("Failed to move cached message to recipient folder: {}",
                            ioException.getMessage(),
                            ioException);
                }
            }
            return null;
        }

        // If successful and for all other errors that are not recoverable, delete the cached message
        cachedMessage.delete();
        return actions;
    }

    private void handleQueuedActions(final Collection<HandleAction> queuedActions) {
        logger.debug("Handling message actions");
        for (var action : queuedActions) {
            logger.debug("Executing action {}", action.getClass().getSimpleName());
            try {
                action.execute(context);
            } catch (Throwable e) {
                logger.warn("Message action failed.", e);
            }
        }
    }

    private void onWebSocketStateChange(final WebSocketConnectionState s) {
        if (s.equals(WebSocketConnectionState.AUTHENTICATION_FAILED)) {
            account.setRegistered(false);
            authenticationFailureListener.call();
        }
    }

    public interface Callable {

        void call();
    }
}
