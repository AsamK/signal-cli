package org.asamk.signal.manager.storage.messageCache;

import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.MessageCacheUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MessageCache {

    private final static Logger logger = LoggerFactory.getLogger(MessageCache.class);

    private final File messageCachePath;

    public MessageCache(final File messageCachePath) {
        this.messageCachePath = messageCachePath;
    }

    public Iterable<CachedMessage> getCachedMessages() {
        if (!messageCachePath.exists()) {
            return Collections.emptyList();
        }

        return Arrays.stream(Objects.requireNonNull(messageCachePath.listFiles())).flatMap(dir -> {
            if (dir.isFile()) {
                return Stream.of(dir);
            }

            final var files = Objects.requireNonNull(dir.listFiles());
            if (files.length == 0) {
                try {
                    Files.delete(dir.toPath());
                } catch (IOException e) {
                    logger.warn("Failed to delete cache dir “{}”, ignoring: {}", dir, e.getMessage());
                }
                return Stream.empty();
            }
            return Arrays.stream(files).filter(File::isFile);
        }).map(CachedMessage::new).collect(Collectors.toList());
    }

    public CachedMessage cacheMessage(SignalServiceEnvelope envelope, RecipientId recipientId) {
        final var now = new Date().getTime();

        try {
            var cacheFile = getMessageCacheFile(recipientId, now, envelope.getTimestamp());
            MessageCacheUtils.storeEnvelope(envelope, cacheFile);
            return new CachedMessage(cacheFile);
        } catch (IOException e) {
            logger.warn("Failed to store encrypted message in disk cache, ignoring: {}", e.getMessage());
            return null;
        }
    }

    public CachedMessage replaceSender(CachedMessage cachedMessage, RecipientId sender) throws IOException {
        final var cacheFile = getMessageCacheFile(sender, cachedMessage.getFile().getName());
        if (cacheFile.equals(cachedMessage.getFile())) {
            return cachedMessage;
        }
        Files.move(cachedMessage.getFile().toPath(), cacheFile.toPath());
        return new CachedMessage(cacheFile);
    }

    private File getMessageCachePath(RecipientId recipientId) {
        if (recipientId == null) {
            return messageCachePath;
        }

        var sender = String.valueOf(recipientId.getId());
        return new File(messageCachePath, sender.replace("/", "_"));
    }

    private File getMessageCacheFile(RecipientId recipientId, String filename) throws IOException {
        var cachePath = getMessageCachePath(recipientId);
        IOUtils.createPrivateDirectories(cachePath);
        return new File(cachePath, filename);
    }

    private File getMessageCacheFile(RecipientId recipientId, long now, long timestamp) throws IOException {
        var cachePath = getMessageCachePath(recipientId);
        IOUtils.createPrivateDirectories(cachePath);
        return new File(cachePath, now + "_" + timestamp);
    }

    public void mergeRecipients(final RecipientId recipientId, final RecipientId toBeMergedRecipientId) {
        final var toBeMergedMessageCachePath = getMessageCachePath(toBeMergedRecipientId);
        if (!toBeMergedMessageCachePath.exists()) {
            return;
        }

        for (var file : Objects.requireNonNull(toBeMergedMessageCachePath.listFiles())) {
            if (!file.isFile()) {
                continue;
            }

            try {
                final var cacheFile = getMessageCacheFile(recipientId, file.getName());
                Files.move(file.toPath(), cacheFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to move cache file “{}”, ignoring: {}", file, e.getMessage());
            }
        }
    }
}
