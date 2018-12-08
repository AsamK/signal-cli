package org.asamk.signal.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

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

    public static void createPrivateDirectories(String directoryPath) throws IOException {
        final File file = new File(directoryPath);
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
}
