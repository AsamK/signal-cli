package org.asamk.signal.manager.internal;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.RecipientIdentifier;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.helper.AccountFileUpdater;
import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.helper.SendHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.KeyUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.nio.file.Path;
import java.security.Security;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typing messages must not be sent when typing indicators are disabled in the account's
 * configuration, like read receipts.
 */
class TypingIndicatorsSettingTest {

    private static final UUID RECIPIENT = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @TempDir
    Path directory;

    private SignalAccount account;
    private ManagerImpl manager;
    private final AtomicInteger sentTypingMessages = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        // ManagerImpl.close() creates the push service socket, whose trust store needs BouncyCastle
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        account = SignalAccount.createLinkedAccount(directory.toFile(),
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

        // Replace the send helper, so nothing is sent to the server
        final var contextField = ManagerImpl.class.getDeclaredField("context");
        contextField.setAccessible(true);
        final var context = (Context) contextField.get(manager);
        final var sendHelperField = Context.class.getDeclaredField("sendHelper");
        sendHelperField.setAccessible(true);
        sendHelperField.set(context, new SendHelper(context) {
            @Override
            public SendMessageResult sendTypingMessage(
                    final SignalServiceTypingMessage message,
                    final RecipientId recipientId
            ) {
                sentTypingMessages.incrementAndGet();
                return SendMessageResult.networkFailure(new SignalServiceAddress(ACI.from(RECIPIENT)));
            }
        });
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void typingMessageIsNotSentWhenTypingIndicatorsAreDisabled() throws Exception {
        account.getConfigurationStore().setTypingIndicators(false);

        final var results = manager.sendTypingMessage(TypingAction.START,
                Set.of(new RecipientIdentifier.Uuid(RECIPIENT)));

        assertEquals(0, sentTypingMessages.get());
        assertTrue(results.results().isEmpty());
    }

    @Test
    void typingMessageIsSentWhenTypingIndicatorsAreEnabled() throws Exception {
        account.getConfigurationStore().setTypingIndicators(true);

        final var results = manager.sendTypingMessage(TypingAction.START,
                Set.of(new RecipientIdentifier.Uuid(RECIPIENT)));

        assertEquals(1, sentTypingMessages.get());
        assertEquals(1, results.results().size());
    }
}
