package org.asamk.signal.manager.helper;

import org.signal.core.models.backup.MessageBackupKey;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Temporary adapter for the link-and-sync encrypted container.
 *
 * <p>The resolved signal-network/libsignal artifacts expose key derivation and frame validation, but no public
 * platform-neutral encrypted-backup reader. This class must be replaced by that dependency primitive before the
 * signal-cli change can be considered upstream-clean.</p>
 */
final class EncryptedHistoryReader implements AutoCloseable {

    private static final int IV_SIZE = 16;
    private static final int MAC_SIZE = 32;
    private static final int MAX_FRAME_SIZE = 64 * 1024 * 1024;

    private final InputStream source;
    private final GZIPInputStream plaintext;

    static EncryptedHistoryReader open(
            final HistoryTransferStore store,
            final HistoryTransferStore.Metadata metadata,
            final MessageBackupKey.BackupKeyMaterial keyMaterial
    ) throws IOException {
        final var length = store.archiveLength(metadata);
        if (length <= IV_SIZE + MAC_SIZE) {
            throw new IOException("History archive is too short");
        }
        validateMac(store, metadata, keyMaterial.getMacKey(), length);

        final var source = store.openArchive(metadata);
        try {
            final var iv = readExactly(source, IV_SIZE);
            final var ciphertextLength = length - IV_SIZE - MAC_SIZE;
            final var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyMaterial.getAesKey(), "AES"),
                    new IvParameterSpec(iv));
            final var limited = new BoundedInputStream(source, ciphertextLength);
            final var plaintext = new GZIPInputStream(new CipherInputStream(limited, cipher));
            return new EncryptedHistoryReader(source, plaintext);
        } catch (Exception e) {
            source.close();
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to initialize history archive decryption", e);
        }
    }

    private EncryptedHistoryReader(final InputStream source, final GZIPInputStream plaintext) {
        this.source = source;
        this.plaintext = plaintext;
    }

    DelimitedMessage next() throws IOException {
        final var first = plaintext.read();
        if (first == -1) {
            return null;
        }

        final var prefix = new ByteArrayOutputStream(5);
        prefix.write(first);
        int value = first & 0x7f;
        int shift = 7;
        int current = first;
        while ((current & 0x80) != 0) {
            if (shift >= 32) {
                throw new IOException("Invalid history frame length prefix");
            }
            current = plaintext.read();
            if (current == -1) {
                throw new EOFException("Truncated history frame length prefix");
            }
            if (shift == 28 && (current & 0xf0) != 0) {
                throw new IOException("Invalid history frame length prefix");
            }
            prefix.write(current);
            value |= (current & 0x7f) << shift;
            shift += 7;
        }
        if (value < 0 || value > MAX_FRAME_SIZE) {
            throw new IOException("Invalid history frame size: " + Integer.toUnsignedLong(value));
        }
        final var payload = readExactly(plaintext, value);
        final var framed = new byte[prefix.size() + payload.length];
        System.arraycopy(prefix.toByteArray(), 0, framed, 0, prefix.size());
        System.arraycopy(payload, 0, framed, prefix.size(), payload.length);
        return new DelimitedMessage(payload, framed);
    }

    @Override
    public void close() throws IOException {
        try {
            plaintext.close();
        } finally {
            source.close();
        }
    }

    private static void validateMac(
            final HistoryTransferStore store,
            final HistoryTransferStore.Metadata metadata,
            final byte[] macKey,
            final long length
    ) throws IOException {
        try (final var input = store.openArchive(metadata)) {
            final var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            var remaining = length - MAC_SIZE;
            final var buffer = new byte[32 * 1024];
            while (remaining > 0) {
                final var read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    throw new EOFException("Truncated history archive");
                }
                mac.update(buffer, 0, read);
                remaining -= read;
            }
            final var expected = readExactly(input, MAC_SIZE);
            if (!MessageDigest.isEqual(mac.doFinal(), expected)) {
                throw new IOException("History archive MAC verification failed");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to verify history archive", e);
        }
    }

    private static byte[] readExactly(final InputStream input, final int length) throws IOException {
        final var data = input.readNBytes(length);
        if (data.length != length) {
            throw new EOFException("Truncated history archive");
        }
        return data;
    }

    record DelimitedMessage(byte[] payload, byte[] framed) {
    }

    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(final InputStream delegate, final long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            final var value = delegate.read();
            if (value == -1) {
                throw new EOFException("Truncated history archive ciphertext");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            final var read = delegate.read(buffer, offset, (int) Math.min(length, remaining));
            if (read == -1) {
                throw new EOFException("Truncated history archive ciphertext");
            }
            remaining -= read;
            return read;
        }

        @Override
        public void close() {
            // The owning EncryptedHistoryReader closes the underlying archive stream.
        }
    }
}
