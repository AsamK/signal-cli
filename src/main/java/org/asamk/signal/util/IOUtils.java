package org.asamk.signal.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;

public class IOUtils {

    private IOUtils() {
    }

    public static String readAll(InputStream in, Charset charset) throws IOException {
        var output = new StringWriter();
        var buffer = new byte[4096];
        int n;
        while (-1 != (n = in.read(buffer))) {
            output.write(new String(buffer, 0, n, charset));
        }
        return output.toString();
    }

    public static File getDataHomeDir() {
        var dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome != null) {
            return new File(dataHome);
        }

        return new File(new File(System.getProperty("user.home"), ".local"), "share");
    }
}
