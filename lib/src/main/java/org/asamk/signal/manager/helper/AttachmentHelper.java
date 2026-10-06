package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.asamk.signal.manager.api.Message.AttachmentDimensions;
import org.asamk.signal.manager.api.Pair;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.internal.SignalDependencies;
import org.asamk.signal.manager.storage.AttachmentStore;
import org.asamk.signal.manager.util.AttachmentUtils;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.MimeUtils;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class AttachmentHelper {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentHelper.class);

    private final SignalDependencies dependencies;
    private final AttachmentStore attachmentStore;
    private final Uploader uploader;
    private final Supplier<File> dataPath;

    public AttachmentHelper(final Context context) {
        this.dependencies = context.getDependencies();
        this.attachmentStore = context.getAttachmentStore();
        this.uploader = new DependenciesUploader(dependencies);
        this.dataPath = () -> context.getAccount().getDataPath();
    }

    AttachmentHelper(final Uploader uploader, final Supplier<File> dataPath) {
        this.dependencies = null;
        this.attachmentStore = null;
        this.uploader = uploader;
        this.dataPath = dataPath;
    }

    public File getAttachmentFile(SignalServiceAttachmentPointer pointer) {
        return attachmentStore.getAttachmentFile(pointer);
    }

    public StreamDetails retrieveAttachment(final String id) throws IOException {
        return attachmentStore.retrieveAttachment(id);
    }

    /**
     * Prepares and uploads all attachments. I/O failures are reported as {@link AttachmentInvalidException}, so
     * callers know that nothing has been sent yet. Attachments after the failing one are not uploaded.
     */
    public List<SignalServiceAttachment> uploadAttachments(
            final List<String> attachments,
            final List<AttachmentDimensions> dimensions,
            final List<String> blurHashes,
            boolean voiceNote
    ) throws AttachmentInvalidException {
        final var attachmentStreams = createAttachmentStreams(attachments, dimensions, blurHashes, voiceNote);

        try {
            // Upload attachments here, so we only upload once even for multiple recipients
            final var attachmentPointers = new ArrayList<SignalServiceAttachment>(attachmentStreams.size());
            for (var i = 0; i < attachmentStreams.size(); i++) {
                final var attachment = attachments.get(i);
                try {
                    attachmentPointers.add(uploader.upload(attachmentStreams.get(i)));
                } catch (IOException e) {
                    throw invalid(describe(attachment, inlineAttachmentLabel(i)), e);
                }
            }
            return attachmentPointers;
        } finally {
            closeAll(attachmentStreams);
        }
    }

    public List<SignalServiceAttachment> uploadAttachments(final List<String> attachments) throws AttachmentInvalidException {
        return uploadAttachments(attachments, List.of(), List.of(), false);
    }

    private List<SignalServiceAttachmentStream> createAttachmentStreams(
            List<String> attachments,
            List<AttachmentDimensions> dimensions,
            List<String> blurHashes,
            boolean voiceNote
    ) throws AttachmentInvalidException {
        if (attachments == null) {
            return List.of();
        }
        final var signalServiceAttachments = new ArrayList<SignalServiceAttachmentStream>(attachments.size());
        for (var i = 0; i < attachments.size(); i++) {
            final var size = i < dimensions.size() ? dimensions.get(i) : null;
            final var blurHash = i < blurHashes.size() && !blurHashes.get(i).isEmpty() ? blurHashes.get(i) : null;
            signalServiceAttachments.add(getAttachmentStream(attachments.get(i),
                    inlineAttachmentLabel(i),
                    size,
                    blurHash,
                    voiceNote));
        }
        return signalServiceAttachments;
    }

    private SignalServiceAttachmentStream getAttachmentStream(
            final String attachment,
            final String inlineLabel,
            final AttachmentDimensions dimensions,
            final String blurHash,
            final boolean voiceNote
    ) throws AttachmentInvalidException {
        final var label = describe(attachment, inlineLabel);
        try {
            final Pair<StreamDetails, Optional<String>> streamDetailsAndFileName;
            if (isInline(attachment)) {
                // Never open inline data as a file, whose errors could echo its payload. Check for the comma
                // before parsing to avoid regex backtracking over long malformed values.
                if (attachment.indexOf(',') < 0) {
                    throw new IOException("Invalid data URI");
                }
                try {
                    streamDetailsAndFileName = Utils.createStreamDetailsFromDataURI(attachment);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid data URI");
                }
            } else {
                // Reject local files that point into the signal-cli data directory
                final var canonical = new File(attachment).getCanonicalFile();
                final var dataPath = this.dataPath.get().getCanonicalFile();
                if (canonical.toPath().startsWith(dataPath.toPath())) {
                    throw new IOException("Attaching files from the signal-cli data directory is not allowed");
                }
                streamDetailsAndFileName = Utils.createStreamDetails(attachment);
            }
            final var streamDetails = streamDetailsAndFileName.first();
            final var uploadSpec = getResumableUploadSpec(streamDetails);
            return AttachmentUtils.createAttachmentStream(streamDetails,
                    streamDetailsAndFileName.second(),
                    voiceNote,
                    dimensions,
                    blurHash,
                    uploadSpec);
        } catch (IOException e) {
            throw invalid(label, e);
        }
    }

    public ResumableUploadSpec getResumableUploadSpec(final StreamDetails streamDetails) throws IOException {
        final var streamLength = streamDetails.getLength();
        final var ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(
                streamLength));
        return uploader.getResumableUploadSpec(ciphertextLength);
    }

    public SignalServiceAttachmentPointer uploadAttachment(String attachment) throws AttachmentInvalidException {
        return uploadAttachment(attachment, "inline attachment");
    }

    public SignalServiceAttachmentPointer uploadAttachment(SignalServiceAttachmentStream attachment) throws IOException {
        return uploader.upload(attachment);
    }

    /**
     * Prepares and uploads a single attachment, such as a quote thumbnail or a link preview image. Any
     * failure is reported as {@link AttachmentInvalidException} with the given label for inline data.
     */
    public SignalServiceAttachmentPointer uploadAttachment(
            final String attachment,
            final String inlineLabel
    ) throws AttachmentInvalidException {
        try (final var attachmentStream = getAttachmentStream(attachment, inlineLabel, null, null, false)) {
            return uploader.upload(attachmentStream);
        } catch (IOException e) {
            throw invalid(describe(attachment, inlineLabel), e);
        }
    }

    /**
     * Uploads the full text of a message that is too long to be sent inline. Failures never include the text.
     */
    public SignalServiceAttachmentPointer uploadLongTextAttachment(final byte[] messageBytes) throws AttachmentInvalidException {
        final var streamDetails = new StreamDetails(new ByteArrayInputStream(messageBytes),
                MimeUtils.LONG_TEXT,
                messageBytes.length);
        try (final var textAttachment = createAttachmentStream(streamDetails)) {
            return uploader.upload(textAttachment);
        } catch (IOException e) {
            throw invalid("long text attachment", e);
        }
    }

    /**
     * Prepares a sticker for sending. The sticker itself is uploaded later by the message sender.
     */
    public SignalServiceAttachmentStream createStickerAttachmentStream(final StreamDetails streamDetails) throws AttachmentInvalidException {
        try {
            return createAttachmentStream(streamDetails);
        } catch (IOException e) {
            throw invalid("sticker", e);
        }
    }

    private SignalServiceAttachmentStream createAttachmentStream(final StreamDetails streamDetails) throws IOException {
        final var uploadSpec = getResumableUploadSpec(streamDetails);
        return AttachmentUtils.createAttachmentStream(streamDetails, Optional.empty(), uploadSpec);
    }

    private static String describe(final String attachment, final String inlineLabel) {
        return isInline(attachment) ? inlineLabel : attachment;
    }

    static AttachmentInvalidException invalid(final String label, final Exception e) {
        final var message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        final var exception = new AttachmentInvalidException(label + ": " + message);
        exception.initCause(e);
        return exception;
    }

    private static boolean isInline(final String attachment) {
        return attachment != null && attachment.regionMatches(true, 0, "data:", 0, 5);
    }

    private static String inlineAttachmentLabel(final int index) {
        return "inline attachment #" + (index + 1);
    }

    private static void closeAll(final List<SignalServiceAttachmentStream> streams) {
        for (final var stream : streams) {
            try {
                stream.close();
            } catch (IOException e) {
                logger.warn("Failed to close attachment stream, ignoring: {}", e.getMessage());
            }
        }
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
            logger.warn("Failed to download attachment ({}), ignoring", pointer.getRemoteId(), e);
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

    interface Uploader {

        ResumableUploadSpec getResumableUploadSpec(long ciphertextLength) throws IOException;

        SignalServiceAttachmentPointer upload(SignalServiceAttachmentStream stream) throws IOException;
    }

    private record DependenciesUploader(SignalDependencies dependencies) implements Uploader {

        @Override
        public ResumableUploadSpec getResumableUploadSpec(final long ciphertextLength) throws IOException {
            return dependencies.getCdnService().getResumableUploadSpecBlocking(ciphertextLength);
        }

        @Override
        public SignalServiceAttachmentPointer upload(final SignalServiceAttachmentStream stream) throws IOException {
            return dependencies.getMessageSender().uploadAttachment(stream);
        }
    }
}
