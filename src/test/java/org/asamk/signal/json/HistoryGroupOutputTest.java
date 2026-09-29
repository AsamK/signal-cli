package org.asamk.signal.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.asamk.signal.manager.Settings;
import org.asamk.signal.manager.api.HistoryExportResult;
import org.asamk.signal.manager.api.ServiceEnvironment;
import org.asamk.signal.manager.helper.Context;
import org.asamk.signal.manager.helper.HistoryTransferHelper;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.util.KeyUtils;
import org.asamk.signal.output.JsonWriterImpl;
import org.asamk.signal.testutil.ManagerMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.backup.MessageBackupKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryGroupOutputTest {

    private static final ACI ACI_ID = ACI.parseOrThrow("11111111-1111-4111-8111-111111111111");

    @TempDir
    Path directory;

    @Test
    void encryptedGroupMessageSerializesWithEmptyLocalGroupStore() throws Exception {
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
        try {
            final var key = new byte[32];
            Arrays.fill(key, (byte) 7);
            writeHistoryState(account, key);

            final var output = new StringWriter();
            final var manager = ManagerMock.create("+15551234567");
            final var handler = new JsonReceiveMessageHandler(manager, new JsonWriterImpl(output));

            assertEquals(new HistoryExportResult(1, 0), new HistoryTransferHelper(context).export(handler, false));

            final var root = new ObjectMapper().readTree(output.toString());
            final var groupInfo = root.path("envelope").path("dataMessage").path("groupInfo");
            assertFalse(groupInfo.path("groupId").asText().isBlank());
            assertTrue(groupInfo.path("groupName").isNull());
            assertEquals("hello", root.path("envelope").path("dataMessage").path("message").asText());
        } finally {
            context.close();
            account.close();
        }
    }

    private static void writeHistoryState(final SignalAccount account, final byte[] key) throws Exception {
        final var generation = "group-fixture";
        final var historyDirectory = account.getAccountDataPath().toPath().resolve("history-transfer");
        Files.createDirectories(historyDirectory);
        final var metadata = new ObjectMapper().createObjectNode();
        metadata.put("version", 2);
        metadata.put("generation", generation);
        metadata.put("aci", ACI_ID.toString());
        metadata.put("backupKey", Base64.getEncoder().encodeToString(key));
        metadata.putNull("cdn");
        metadata.putNull("cdnKey");
        metadata.put("createdAt", 0);
        new ObjectMapper().writeValue(historyDirectory.resolve("metadata.json").toFile(), metadata);

        final var material = new MessageBackupKey(key).deriveBackupSecrets(ACI_ID, null);
        Files.write(historyDirectory.resolve("archive-" + generation + ".bin"), encrypt(material,
                backupInfo(),
                accountFrame(),
                selfRecipientFrame(),
                contactRecipientFrame(),
                groupRecipientFrame(),
                groupChatFrame(),
                groupIncomingMessageFrame()));
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
}
