package org.asamk.signal.manager.util;

import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

public class IOUtils {

    public static File createTempFile() throws IOException {
        final File tempFile = File.createTempFile("signal-cli_tmp_", ".tmp");
        tempFile.deleteOnExit();
        return tempFile;
    }

    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Util.copy(in, baos);
        return baos.toByteArray();
    }

    public static void createPrivateDirectories(File file) throws IOException {
        if (file.exists()) {
            return;
        }

        final Path path = file.toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
            Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(path);
        }
    }

    public static void createPrivateFile(File path) throws IOException {
        final Path file = path.toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createFile(file);
        }
    }

    public static void copyStreamToFile(InputStream input, File outputFile) throws IOException {
        copyStreamToFile(input, outputFile, 8192);
    }

    public static void copyStreamToFile(InputStream input, File outputFile, int bufferSize) throws IOException {
        try (OutputStream output = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[bufferSize];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
