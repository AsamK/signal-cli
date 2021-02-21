package org.asamk.signal.manager.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

public class IOUtils {

    public static File createTempFile() throws IOException {
        final var tempFile = File.createTempFile("signal-cli_tmp_", ".tmp");
        tempFile.deleteOnExit();
        return tempFile;
    }

    public static byte[] readFully(InputStream in) throws IOException {
        var baos = new ByteArrayOutputStream();
        IOUtils.copyStream(in, baos);
        return baos.toByteArray();
    }

    public static void createPrivateDirectories(File file) throws IOException {
        if (file.exists()) {
            return;
        }

        final var path = file.toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
            Files.createDirectories(path, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(path);
        }
    }

    public static void createPrivateFile(File path) throws IOException {
        final var file = path.toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createFile(file);
        }
    }

    public static void copyFileToStream(File inputFile, OutputStream output) throws IOException {
        try (InputStream inputStream = new FileInputStream(inputFile)) {
            copyStream(inputStream, output);
        }
    }

    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        copyStream(input, output, 4096);
    }

    public static void copyStream(InputStream input, OutputStream output, int bufferSize) throws IOException {
        var buffer = new byte[bufferSize];
        int read;

        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
