package org.asamk.signal.manager.storage.messageCache;

import org.asamk.signal.manager.util.MessageCacheUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class CachedMessage {

    private final static Logger logger = LoggerFactory.getLogger(CachedMessage.class);

    private final File file;

    CachedMessage(final File file) {
        this.file = file;
    }

    File getFile() {
        return file;
    }

    public SignalServiceEnvelope loadEnvelope() {
        try {
            return MessageCacheUtils.loadEnvelope(file);
        } catch (Exception e) {
            logger.error("Failed to load cached message envelope “{}”: {}", file, e.getMessage());
            return null;
        }
    }

    public void delete() {
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            logger.warn("Failed to delete cached message file “{}”, ignoring: {}", file, e.getMessage());
        }
        // Delete parent directory, if empty
        try {
            Files.delete(file.toPath().getParent());
        } catch (IOException ignored) {
        }
    }
}
