package org.asamk.signal.manager.helper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.backup.MessageBackupKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptedHistoryReaderTest {

    @TempDir
    Path directory;

    @Test
    void validatesDecryptsAndReadsDelimitedFrames() throws Exception {
        final var store = new HistoryTransferStore(directory.resolve("history-transfer"));
        final var key = new byte[32];
        Arrays.fill(key, (byte) 7);
        final var metadata = store.createPending("11111111-1111-4111-8111-111111111111", key);
        final var material = new MessageBackupKey(key).deriveBackupSecrets(ACI.parseOrThrow(
                "11111111-1111-4111-8111-111111111111"), null);
        final var encrypted = encrypt(material, new byte[]{1, 2}, new byte[]{3, 4, 5});
        store.writeArchive(metadata, output -> output.write(encrypted));

        try (final var reader = EncryptedHistoryReader.open(store, metadata, material)) {
            assertArrayEquals(new byte[]{1, 2}, reader.next().payload());
            assertArrayEquals(new byte[]{3, 4, 5}, reader.next().payload());
            assertNull(reader.next());
        }

        encrypted[encrypted.length - 1] ^= 1;
        store.writeArchive(metadata, output -> output.write(encrypted));
        assertThrows(IOException.class, () -> EncryptedHistoryReader.open(store, metadata, material));
    }

    @Test
    void rejectsOverflowingFrameLengthPrefix() throws Exception {
        final var store = new HistoryTransferStore(directory.resolve("history-transfer"));
        final var key = new byte[32];
        Arrays.fill(key, (byte) 7);
        final var metadata = store.createPending("11111111-1111-4111-8111-111111111111", key);
        final var material = new MessageBackupKey(key).deriveBackupSecrets(ACI.parseOrThrow(
                "11111111-1111-4111-8111-111111111111"), null);
        final var encrypted = encryptPlaintext(material,
                new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x10});
        store.writeArchive(metadata, output -> output.write(encrypted));

        try (final var reader = EncryptedHistoryReader.open(store, metadata, material)) {
            assertThrows(IOException.class, reader::next);
        }
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

        return encryptPlaintext(material, plaintext.toByteArray());
    }

    private static byte[] encryptPlaintext(
            final MessageBackupKey.BackupKeyMaterial material,
            final byte[] plaintext
    ) throws Exception {
        final var compressed = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(plaintext);
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
