package org.asamk.signal.util;

import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
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

    private IOUtils() {
    }

    public static File createTempFile() throws IOException {
        return File.createTempFile("signal_tmp_", ".tmp");
    }

    public static String readAll(InputStream in, Charset charset) throws IOException {
        StringWriter output = new StringWriter();
        byte[] buffer = new byte[4096];
        int n;
        while (-1 != (n = in.read(buffer))) {
            output.write(new String(buffer, 0, n, charset));
        }
        return output.toString();
    }

    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Util.copy(in, baos);
        return baos.toByteArray();
    }

    public static void createPrivateDirectories(String directoryPath) throws IOException {
        final File file = new File(directoryPath);
        createPrivateDirectories(file);
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

    public static void createPrivateFile(String path) throws IOException {
        final Path file = new File(path).toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createFile(file);
        }
    }

    public static String getDataHomeDir() {
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome != null) {
            return dataHome;
        }

        return System.getProperty("user.home") + "/.local/share";
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
