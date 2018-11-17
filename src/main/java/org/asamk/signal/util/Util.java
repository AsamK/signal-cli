package org.asamk.signal.util;

import java.io.File;
import java.io.IOException;

public class Util {
    public static File createTempFile() throws IOException {
        return File.createTempFile("signal_tmp_", ".tmp");
    }
}
