package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.AttachmentStore;
import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AttachmentHelper {

    private final static Logger logger = LoggerFactory.getLogger(AttachmentHelper.class);

    private final SignalDependencies dependencies;
    private final AttachmentStore attachmentStore;

    public AttachmentHelper(
            final SignalDependencies dependencies, final AttachmentStore attachmentStore
    ) {
        this.dependencies = dependencies;
        this.attachmentStore = attachmentStore;
    }

    public File getAttachmentFile(SignalServiceAttachmentRemoteId attachmentId) {
        return attachmentStore.getAttachmentFile(attachmentId);
    }

    public List<SignalServiceAttachment> uploadAttachments(final List<String> attachments) throws AttachmentInvalidException, IOException {
        var attachmentStreams = AttachmentUtils.getSignalServiceAttachments(attachments);

        // Upload attachments here, so we only upload once even for multiple recipients
        var messageSender = dependencies.getMessageSender();
        var attachmentPointers = new ArrayList<SignalServiceAttachment>(attachmentStreams.size());
        for (var attachment : attachmentStreams) {
            if (attachment.isStream()) {
                attachmentPointers.add(messageSender.uploadAttachment(attachment.asStream()));
            } else if (attachment.isPointer()) {
                attachmentPointers.add(attachment.asPointer());
            }
        }
        return attachmentPointers;
    }

    public void downloadAttachment(final SignalServiceAttachment attachment) {
        if (!attachment.isPointer()) {
            logger.warn("Invalid state, can't store an attachment stream.");
        }

        var pointer = attachment.asPointer();
        if (pointer.getPreview().isPresent()) {
            final var preview = pointer.getPreview().get();
            try {
                attachmentStore.storeAttachmentPreview(pointer.getRemoteId(),
                        outputStream -> outputStream.write(preview, 0, preview.length));
            } catch (IOException e) {
                logger.warn("Failed to download attachment preview, ignoring: {}", e.getMessage());
            }
        }

        try {
            attachmentStore.storeAttachment(pointer.getRemoteId(),
                    outputStream -> this.retrieveAttachment(pointer, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download attachment ({}), ignoring: {}", pointer.getRemoteId(), e.getMessage());
        }
    }

    void retrieveAttachment(SignalServiceAttachment attachment, OutputStream outputStream) throws IOException {
        retrieveAttachment(attachment, input -> IOUtils.copyStream(input, outputStream));
    }

    public void retrieveAttachment(
            SignalServiceAttachment attachment, AttachmentHandler consumer
    ) throws IOException {
        if (attachment.isStream()) {
            var input = attachment.asStream().getInputStream();
            // don't close input stream here, it might be reused later (e.g. with contact sync messages ...)
            consumer.handle(input);
            return;
        }

        var tmpFile = IOUtils.createTempFile();
        try (var input = retrieveAttachmentAsStream(attachment.asPointer(), tmpFile)) {
            consumer.handle(input);
        } finally {
            try {
                Files.delete(tmpFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete received attachment temp file “{}”, ignoring: {}",
                        tmpFile,
                        e.getMessage());
            }
        }
    }

    private InputStream retrieveAttachmentAsStream(
            SignalServiceAttachmentPointer pointer, File tmpFile
    ) throws IOException {
        try {
            return dependencies.getMessageReceiver()
                    .retrieveAttachment(pointer, tmpFile, ServiceConfig.MAX_ATTACHMENT_SIZE);
        } catch (MissingConfigurationException | InvalidMessageException e) {
            throw new IOException(e);
        }
    }

    @FunctionalInterface
    public interface AttachmentHandler {

        void handle(InputStream inputStream) throws IOException;
    }
}
