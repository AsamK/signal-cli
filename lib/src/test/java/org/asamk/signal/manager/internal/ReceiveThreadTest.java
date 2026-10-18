package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.helper.AccountFileUpdater;
import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.helper.ReceiveHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceIdType;

import java.nio.file.Path;
import java.security.Security;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
class ReceiveThreadTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path directory;

    private ManagerImpl manager;
    private FakeReceiveHelper receiveHelper;

    @BeforeEach
    void setUp() throws Exception {
        // ManagerImpl.close() creates the push service socket, whose trust store needs BouncyCastle
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        final var account = SignalAccount.createLinkedAccount(directory.toFile(),
                "account",
                ServiceEnvironment.STAGING,
                Settings.DEFAULT);
        account.setProvisioningData(null,
                ACI.parseOrThrow("11111111-1111-4111-8111-111111111111"),
                null,
                "test-password",
                new byte[]{1},
                KeyUtils.generateIdentityKeyPair(),
                null,
                KeyUtils.createProfileKey(),
                null,
                new byte[32],
                null);
        account.finishLinking(2, KeyUtils.generatePreKeysForType(account.getAccountData(ServiceIdType.ACI)), null);
        manager = new ManagerImpl(account,
                PathConfig.createDefault(directory.toFile()),
                new AccountFileUpdater() {
                    @Override
                    public void updateAccountIdentifiers(final String number, final ACI aci) {
                    }

                    @Override
                    public void removeAccount() {
                    }
                },
                ServiceConfig.getServiceEnvironmentConfig(ServiceEnvironment.STAGING, "signal-cli-test"),
                "signal-cli-test");

        // Replace the receive helper, so the receive thread doesn't connect to the server
        final var contextField = ManagerImpl.class.getDeclaredField("context");
        contextField.setAccessible(true);
        final var context = (Context) contextField.get(manager);
        receiveHelper = new FakeReceiveHelper(context);
        final var receiveHelperField = Context.class.getDeclaredField("receiveHelper");
        receiveHelperField.setAccessible(true);
        receiveHelperField.set(context, receiveHelper);
    }

    @AfterEach
    void tearDown() {
        receiveHelper.setHoldStoppingLoops(false);
        manager.close();
    }

    @Test
    void handlerAddedWhileReceiveThreadIsStoppingDoesNotStartSecondReceiveThread() throws Exception {
        final var first = newHandler();
        final var second = newHandler();

        manager.addReceiveHandler(first);
        assertTrue(receiveHelper.await(() -> receiveHelper.runningLoops == 1));

        receiveHelper.setHoldStoppingLoops(true);
        final var remover = Thread.ofPlatform().start(() -> manager.removeReceiveHandler(first));
        assertTrue(receiveHelper.await(() -> receiveHelper.stoppingLoops == 1));

        // Like a client that reconnects while the receive thread for its previous connection is still stopping
        manager.addReceiveHandler(second);
        assertTrue(receiveHelper.await(() -> receiveHelper.startedThreads == 1));

        receiveHelper.setHoldStoppingLoops(false);
        assertTrue(remover.join(TIMEOUT));
        assertTrue(receiveHelper.await(() -> receiveHelper.startedLoops == 2 && receiveHelper.runningLoops == 1));
        assertTrue(receiveHelper.await(() -> receiveHelper.startedThreads == 2 && receiveHelper.maxRunningLoops == 1));

        manager.removeReceiveHandler(second);
        assertTrue(receiveHelper.await(() -> receiveHelper.runningLoops == 0));
    }

    @Test
    void stopRequestedForStoppingReceiveThreadDoesNotStopNextReceiveThread() throws Exception {
        final var first = newHandler();
        final var second = newHandler();
        final var third = newHandler();

        manager.addReceiveHandler(first);
        assertTrue(receiveHelper.await(() -> receiveHelper.runningLoops == 1));

        receiveHelper.setHoldStoppingLoops(true);
        final var firstRemover = Thread.ofPlatform().start(() -> manager.removeReceiveHandler(first));
        assertTrue(receiveHelper.await(() -> receiveHelper.stoppingLoops == 1));
        manager.addReceiveHandler(second);
        final var secondRemover = Thread.ofPlatform().start(() -> manager.removeReceiveHandler(second));
        assertTrue(receiveHelper.await(() -> receiveHelper.stopRequests == 2));

        receiveHelper.setHoldStoppingLoops(false);
        assertTrue(firstRemover.join(TIMEOUT));
        assertTrue(secondRemover.join(TIMEOUT));
        assertTrue(receiveHelper.await(() -> receiveHelper.runningLoops == 0 && receiveHelper.startedThreads == 1));

        manager.addReceiveHandler(third);
        assertTrue(receiveHelper.await(() -> receiveHelper.startedLoops == 2 && receiveHelper.runningLoops == 1));
        assertTrue(receiveHelper.await(() -> receiveHelper.startedThreads == 2 && receiveHelper.maxRunningLoops == 1));
    }

    private static Manager.ReceiveMessageHandler newHandler() {
        // An anonymous class instead of a lambda, so every handler is a separate instance
        return new Manager.ReceiveMessageHandler() {
            @Override
            public void handleMessage(final MessageEnvelope envelope, final Throwable e) {
            }
        };
    }

    /**
     * Instead of receiving messages from the server, a receive loop waits until it has been asked to stop.
     * While holdStoppingLoops is set, a loop that has been asked to stop doesn't return yet, like a loop that
     * is still busy when the last handler is removed.
     */
    private static class FakeReceiveHelper extends ReceiveHelper {

        private final Object lock = new Object();
        private boolean stopRequested;
        private boolean holdStoppingLoops;
        private int stopRequests;
        private int startedThreads;
        private int startedLoops;
        private int runningLoops;
        private int maxRunningLoops;
        private int stoppingLoops;

        FakeReceiveHelper(final Context context) {
            super(context);
        }

        @Override
        public boolean requestStopReceiveMessages() {
            super.requestStopReceiveMessages();
            synchronized (lock) {
                stopRequested = true;
                stopRequests++;
                lock.notifyAll();
            }
            // The fake loop isn't blocked in a read that needs to be interrupted
            return false;
        }

        @Override
        public void clearStopRequest() {
            super.clearStopRequest();
            synchronized (lock) {
                stopRequested = false;
            }
        }

        @Override
        public void receiveMessagesContinuously(final Manager.ReceiveMessageHandler handler) {
            synchronized (lock) {
                startedThreads++;
                lock.notifyAll();
            }
            super.receiveMessagesContinuously(handler);
        }

        @Override
        public void receiveMessages(
                final Optional<Duration> timeout,
                final Integer maxMessages,
                final Manager.ReceiveMessageHandler handler
        ) {
            synchronized (lock) {
                startedLoops++;
                runningLoops++;
                maxRunningLoops = Math.max(maxRunningLoops, runningLoops);
                lock.notifyAll();
                if (await(() -> stopRequested)) {
                    stoppingLoops++;
                    lock.notifyAll();
                    await(() -> !holdStoppingLoops);
                    stoppingLoops--;
                }
                runningLoops--;
                lock.notifyAll();
            }
        }

        void setHoldStoppingLoops(final boolean hold) {
            synchronized (lock) {
                holdStoppingLoops = hold;
                lock.notifyAll();
            }
        }

        /**
         * Waits until the condition, which is evaluated while holding the lock, is true.
         * Returns false if it didn't become true within the timeout.
         */
        boolean await(final BooleanSupplier condition) {
            synchronized (lock) {
                final var deadline = System.nanoTime() + TIMEOUT.toNanos();
                while (!condition.getAsBoolean()) {
                    final var remainingMillis = (deadline - System.nanoTime()) / 1_000_000;
                    if (remainingMillis <= 0) {
                        return false;
                    }
                    try {
                        lock.wait(remainingMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return true;
            }
        }
    }
}
