package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.AttachmentStore;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.Utils;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AttachmentHelper {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentHelper.class);

    private final SignalDependencies dependencies;
    private final AttachmentStore attachmentStore;
    private final Context context;

    public AttachmentHelper(final Context context) {
        this.context = context;
        this.dependencies = context.getDependencies();
        this.attachmentStore = context.getAttachmentStore();
    }

    public File getAttachmentFile(SignalServiceAttachmentPointer pointer) {
        return attachmentStore.getAttachmentFile(pointer);
    }

    public StreamDetails retrieveAttachment(final String id) throws IOException {
        return attachmentStore.retrieveAttachment(id);
    }

    public List<SignalServiceAttachment> uploadAttachments(
            final List<String> attachments,
            boolean voiceNote
    ) throws AttachmentInvalidException, IOException {
        final var attachmentStreams = createAttachmentStreams(attachments, voiceNote);

        try {
            // Upload attachments here, so we only upload once even for multiple recipients
            final var attachmentPointers = new ArrayList<SignalServiceAttachment>(attachmentStreams.size());
            for (final var attachmentStream : attachmentStreams) {
                attachmentPointers.add(uploadAttachment(attachmentStream));
            }
            return attachmentPointers;
        } finally {
            for (final var attachmentStream : attachmentStreams) {
                attachmentStream.close();
            }
        }
    }

    public List<SignalServiceAttachment> uploadAttachments(final List<String> attachments) throws AttachmentInvalidException, IOException {
        return uploadAttachments(attachments, false);
    }

    private List<SignalServiceAttachmentStream> createAttachmentStreams(
            List<String> attachments,
            boolean voiceNote
    ) throws AttachmentInvalidException, IOException {
        if (attachments == null) {
            return null;
        }
        final var signalServiceAttachments = new ArrayList<SignalServiceAttachmentStream>(attachments.size());
        for (var attachment : attachments) {
            final var attachmentStream = getAttachmentStream(attachment, voiceNote);
            signalServiceAttachments.add(attachmentStream);
        }
        return signalServiceAttachments;
    }

    private SignalServiceAttachmentStream getAttachmentStream(
            final String attachment,
            final boolean voiceNote
    ) throws AttachmentInvalidException {
        try {
            // Reject local files that point into the signal-cli data directory
            if (attachment != null && !attachment.startsWith("data:")) {
                try {
                    final var file = new File(attachment);
                    final var canonical = file.getCanonicalFile();
                    final var dataPath = context.getAccount().getDataPath().getCanonicalFile();
                    if (canonical.toPath().startsWith(dataPath.toPath())) {
                        throw new AttachmentInvalidException(attachment,
                                new IOException("Attaching files from the signal-cli data directory is not allowed"));
                    }
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }

            final var streamDetailsAndFileName = Utils.createStreamDetails(attachment);
            final var streamDetails = streamDetailsAndFileName.first();
            final var uploadSpec = getResumableUploadSpec(streamDetails);

            return AttachmentUtils.createAttachmentStream(streamDetails,
                    streamDetailsAndFileName.second(),
                    voiceNote,
                    uploadSpec);
        } catch (IOException e) {
            throw new AttachmentInvalidException(attachment, e);
        }
    }

    public ResumableUploadSpec getResumableUploadSpec(final StreamDetails streamDetails) throws IOException {
        final var streamLength = streamDetails.getLength();
        final var ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(
                streamLength));
        return dependencies.getCdnService().getResumableUploadSpecBlocking(ciphertextLength);
    }

    public SignalServiceAttachmentPointer uploadAttachment(String attachment) throws IOException, AttachmentInvalidException {
        final var attachmentStream = getAttachmentStream(attachment, false);
        return uploadAttachment(attachmentStream);
    }

    public SignalServiceAttachmentPointer uploadAttachment(SignalServiceAttachmentStream attachment) throws IOException {
        var messageSender = dependencies.getMessageSender();
        return messageSender.uploadAttachment(attachment);
    }

    public void downloadAttachment(final SignalServiceAttachment attachment) {
        if (!attachment.isPointer()) {
            logger.warn("Invalid state, can't store an attachment stream.");
        }

        var pointer = attachment.asPointer();
        if (pointer.getPreview().isPresent()) {
            final var preview = pointer.getPreview().get();
            try {
                attachmentStore.storeAttachmentPreview(pointer,
                        outputStream -> outputStream.write(preview, 0, preview.length));
            } catch (IOException e) {
                logger.warn("Failed to download attachment preview, ignoring: {}", e.getMessage());
            }
        }

        try {
            attachmentStore.storeAttachment(pointer, outputStream -> this.retrieveAttachment(pointer, outputStream));
        } catch (IOException e) {
            logger.warn("Failed to download attachment ({}), ignoring: {}", pointer.getRemoteId(), e.getMessage());
        }
    }

    void retrieveAttachment(SignalServiceAttachment attachment, OutputStream outputStream) throws IOException {
        retrieveAttachment(attachment, input -> IOUtils.copyStream(input, outputStream));
    }

    public void retrieveAttachment(SignalServiceAttachment attachment, AttachmentHandler consumer) throws IOException {
        if (attachment.isStream()) {
            var input = attachment.asStream().getInputStream();
            // don't close input stream here, it might be reused later (e.g. with contact sync messages ...)
            consumer.handle(input);
            return;
        }

        final var pointer = attachment.asPointer();
        logger.debug("Retrieving attachment {} with size {}", pointer.getRemoteId(), pointer.getSize());
        var tmpFile = IOUtils.createTempFile();
        try (var input = retrieveAttachmentAsStream(pointer, tmpFile)) {
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
            SignalServiceAttachmentPointer pointer,
            File tmpFile
    ) throws IOException {
        if (pointer.getDigest().isEmpty()) {
            throw new IOException("Attachment pointer has no digest.");
        }
        try {
            return dependencies.getMessageReceiver()
                    .retrieveAttachment(pointer,
                            tmpFile,
                            ServiceConfig.MAX_ATTACHMENT_SIZE,
                            AttachmentCipherInputStream.IntegrityCheck.forEncryptedDigest(pointer.getDigest().get()));
        } catch (MissingConfigurationException | InvalidMessageException e) {
            throw new IOException(e);
        }
    }

    @FunctionalInterface
    public interface AttachmentHandler {

        void handle(InputStream inputStream) throws IOException;
    }
}
