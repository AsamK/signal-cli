package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.HistoryExportResult;
import org.asamk.signal.manager.api.MessageEnvelope;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.backup.MessageBackupKey;
import org.whispersystems.signalservice.api.link.TransferArchiveResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTransferHelperTest {

    private static final ACI ACI_ID = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");

    @TempDir
    Path directory;

    @Test
    void classifiesArchiveAndContinueWithoutUpload() throws Exception {
        final var archive = HistoryTransferHelper.classifyTransferResponse(new TransferArchiveResponse(3,
                "archive-key",
                null));
        assertFalse(archive.declined());
        assertEquals(3, archive.cdn());
        assertEquals("archive-key", archive.cdnKey());

        final var declined = HistoryTransferHelper.classifyTransferResponse(new TransferArchiveResponse(null,
                null,
                TransferArchiveResponse.ERROR_CONTINUE_WITHOUT_UPLOAD));
        assertTrue(declined.declined());
    }

    @Test
    void rejectsRelinkAndEmptyResponses() {
        assertThrows(IOException.class,
                () -> HistoryTransferHelper.classifyTransferResponse(new TransferArchiveResponse(null,
                        null,
                        TransferArchiveResponse.ERROR_RELINK_REQUESTED)));
        assertThrows(IOException.class,
                () -> HistoryTransferHelper.classifyTransferResponse(new TransferArchiveResponse(null, null, null)));
    }

    @Test
    void retriesDelayedPollsUntilArchiveIsAvailable() throws Exception {
        final var clock = new AtomicLong();
        final var polls = new AtomicInteger();

        final var response = HistoryTransferHelper.waitForArchive(() -> {
            final var poll = polls.incrementAndGet();
            clock.addAndGet(Duration.ofSeconds(5).toNanos());
            return poll < 3 ? null : new TransferArchiveResponse(3, "archive-key", null);
        }, Duration.ofSeconds(20), clock::get);

        assertEquals(3, polls.get());
        assertEquals("archive-key", response.getKey());
    }

    @Test
    void timesOutDeterministicallyWhenPollsNeverProduceArchive() {
        final var clock = new AtomicLong();
        final var polls = new AtomicInteger();

        final var error = assertThrows(IOException.class,
                () -> HistoryTransferHelper.waitForArchive(() -> {
                    polls.incrementAndGet();
                    clock.addAndGet(Duration.ofSeconds(5).toNanos());
                    return null;
                }, Duration.ofSeconds(12), clock::get));

        assertEquals(3, polls.get());
        assertTrue(error.getCause() instanceof java.util.concurrent.TimeoutException);
    }

    @Test
    void validArchiveIsRepeatableAndDeletedOnlyAfterRequestedExport() throws Exception {
        try (final var fixture = createFixture()) {
            assertEquals(new HistoryExportResult(1, 0), fixture.helper().export(Manager.ReceiveMessageHandler.EMPTY, false));
            assertTrue(fixture.store().hasArchive());
            assertEquals(new HistoryExportResult(1, 0), fixture.helper().export(Manager.ReceiveMessageHandler.EMPTY, false));
            assertTrue(fixture.store().hasArchive());

            assertEquals(new HistoryExportResult(1, 0), fixture.helper().export(Manager.ReceiveMessageHandler.EMPTY, true));
            assertFalse(Files.exists(fixture.store().directory()));
        }
    }

    @Test
    void corruptArchiveIsRetainedWhenDeleteWasRequested() throws Exception {
        try (final var fixture = createFixture()) {
            final var corrupted = Files.readAllBytes(fixture.store().archivePath());
            corrupted[corrupted.length - 1] ^= 1;
            fixture.store().writeArchive(output -> output.write(corrupted));

            assertThrows(IOException.class,
                    () -> fixture.helper().export(Manager.ReceiveMessageHandler.EMPTY, true));
            assertTrue(fixture.store().hasArchive());
            assertTrue(Files.isRegularFile(fixture.store().metadataPath()));
        }
    }

    @Test
    void outputFailureRetainsArchiveWhenDeleteWasRequested() throws Exception {
        try (final var fixture = createFixture()) {
            assertThrows(AssertionError.class,
                    () -> fixture.helper().export((envelope, exception) -> {
                        throw new AssertionError("broken output");
                    }, true));
            assertTrue(fixture.store().hasArchive());
            assertTrue(Files.isRegularFile(fixture.store().metadataPath()));
        }
    }

    @Test
    void validEncryptedGroupMessageDoesNotDependOnLocalGroupState() throws Exception {
        try (final var fixture = createFixture(true)) {
            final var exported = new AtomicReference<MessageEnvelope>();

            assertEquals(new HistoryExportResult(1, 0), fixture.helper().export(
                    (envelope, exception) -> exported.set(envelope), false));
            assertTrue(exported.get().data().orElseThrow().groupContext().isPresent());
        }
    }

    private Fixture createFixture() throws Exception {
        return createFixture(false);
    }

    private Fixture createFixture(final boolean groupMessage) throws Exception {
        final var account = SignalAccount.createLinkedAccount(directory.toFile(),
                "account",
                ServiceEnvironment.STAGING,
                Settings.DEFAULT);
        account.setProvisioningData(null,
                ACI_ID,
                null,
                "test-password",
                new byte[]{1},
                KeyUtils.generateIdentityKeyPair(),
                null,
                KeyUtils.createProfileKey(),
                null,
                new byte[32],
                null);
        final var context = new Context(account, null, null, null, null, null);
        final var store = new HistoryTransferStore(account);
        final var key = new byte[32];
        Arrays.fill(key, (byte) 7);
        store.createPending(ACI_ID.toString(), key);
        final var material = new MessageBackupKey(key).deriveBackupSecrets(ACI_ID, null);
        final var encryptedArchive = groupMessage
                ? encrypt(material,
                        backupInfo(),
                        accountFrame(),
                        selfRecipientFrame(),
                        contactRecipientFrame(),
                        groupRecipientFrame(),
                        groupChatFrame(),
                        groupIncomingMessageFrame())
                : encrypt(material,
                        backupInfo(),
                        accountFrame(),
                        selfRecipientFrame(),
                        contactRecipientFrame(),
                        chatFrame(),
                        incomingMessageFrame());
        store.writeArchive(output -> output.write(encryptedArchive));
        return new Fixture(account, context, store, new HistoryTransferHelper(context));
    }

    private static byte[] backupInfo() throws IOException {
        final var output = new ByteArrayOutputStream();
        output.write(new byte[]{8, 1, 16, (byte) 232, 7, 26, 32});
        output.write(new byte[32]);
        return output.toByteArray();
    }

    private static byte[] accountFrame() throws IOException {
        final var output = new ByteArrayOutputStream();
        output.write(new byte[]{10, 48, 10, 32});
        output.write(new byte[32]);
        output.write(new byte[]{74, 12, (byte) 136, 1, 2, (byte) 184, 1, 1, (byte) 224, 1, 1, (byte) 232, 1, 1});
        return output.toByteArray();
    }

    private static byte[] selfRecipientFrame() {
        return new byte[]{18, 4, 8, 1, 42, 0};
    }

    private static byte[] contactRecipientFrame() throws IOException {
        final var output = new ByteArrayOutputStream();
        output.write(new byte[]{18, 24, 8, 2, 18, 20, 10, 16});
        output.write(new byte[]{
                0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x42, 0x22,
                (byte) 0x82, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22
        });
        output.write(new byte[]{58, 0});
        return output.toByteArray();
    }

    private static byte[] chatFrame() {
        return new byte[]{26, 4, 8, 3, 16, 2};
    }

    private static byte[] groupRecipientFrame() throws IOException {
        final var output = new ByteArrayOutputStream();
        output.write(new byte[]{18, 40, 8, 4, 26, 36, 10, 32});
        final var masterKey = new byte[32];
        Arrays.fill(masterKey, (byte) 9);
        output.write(masterKey);
        output.write(new byte[]{42, 0});
        return output.toByteArray();
    }

    private static byte[] groupChatFrame() {
        return new byte[]{26, 4, 8, 5, 16, 4};
    }

    private static byte[] incomingMessageFrame() {
        return new byte[]{
                34, 27, 8, 3, 16, 2, 24, 100,
                66, 8, 8, 102, 16, 101, 24, 1, 32, 1,
                90, 9, 18, 7, 10, 5, 104, 101, 108, 108, 111
        };
    }

    private static byte[] groupIncomingMessageFrame() {
        return new byte[]{
                34, 27, 8, 5, 16, 2, 24, 100,
                66, 8, 8, 102, 16, 101, 24, 1, 32, 1,
                90, 9, 18, 7, 10, 5, 104, 101, 108, 108, 111
        };
    }

    private static byte[] encrypt(
            final MessageBackupKey.BackupKeyMaterial material,
            final byte[]... messages
    ) throws Exception {
        final var plaintext = new ByteArrayOutputStream();
        for (final var message : messages) {
            writeVarint(plaintext, message.length);
            plaintext.write(message);
        }

        final var compressed = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(plaintext.toByteArray());
        }

        final var iv = new byte[16];
        Arrays.fill(iv, (byte) 3);
        final var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(material.getAesKey(), "AES"),
                new IvParameterSpec(iv));
        final var ciphertext = cipher.doFinal(compressed.toByteArray());

        final var authenticated = new ByteArrayOutputStream();
        authenticated.write(iv);
        authenticated.write(ciphertext);
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(material.getMacKey(), "HmacSHA256"));
        authenticated.write(mac.doFinal(authenticated.toByteArray()));
        return authenticated.toByteArray();
    }

    private static void writeVarint(final OutputStream output, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            output.write((value & 0x7f) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private record Fixture(
            SignalAccount account,
            Context context,
            HistoryTransferStore store,
            HistoryTransferHelper helper
    ) implements AutoCloseable {

        @Override
        public void close() {
            context.close();
            account.close();
        }
    }
}
