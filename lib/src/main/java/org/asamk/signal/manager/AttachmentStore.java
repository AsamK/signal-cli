package org.asamk.signal.manager;

import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.MimeUtils;
import org.asamk.signal.manager.util.Utils;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class AttachmentStore {

    private final File attachmentsPath;

    public AttachmentStore(final File attachmentsPath) {
        this.attachmentsPath = attachmentsPath;
    }

    public void storeAttachmentPreview(
            final SignalServiceAttachmentPointer pointer, final AttachmentStorer storer
    ) throws IOException {
        storeAttachment(getAttachmentPreviewFile(pointer.getRemoteId(),
                pointer.getFileName(),
                Optional.ofNullable(pointer.getContentType())), storer);
    }

    public void storeAttachment(
            final SignalServiceAttachmentPointer pointer, final AttachmentStorer storer
    ) throws IOException {
        storeAttachment(getAttachmentFile(pointer), storer);
    }

    public File getAttachmentFile(final SignalServiceAttachmentPointer pointer) {
        return getAttachmentFile(pointer.getRemoteId(),
                pointer.getFileName(),
                Optional.ofNullable(pointer.getContentType()));
    }

    public StreamDetails retrieveAttachment(final String id) throws IOException {
        final var attachmentFile = new File(attachmentsPath, id);
        return Utils.createStreamDetailsFromFile(attachmentFile);
    }

    private void storeAttachment(final File attachmentFile, final AttachmentStorer storer) throws IOException {
        createAttachmentsDir();
        try (OutputStream output = new FileOutputStream(attachmentFile)) {
            storer.store(output);
        }
    }

    private File getAttachmentPreviewFile(
            SignalServiceAttachmentRemoteId attachmentId, Optional<String> filename, Optional<String> contentType
    ) {
        final var extension = getAttachmentExtension(filename, contentType);
        return new File(attachmentsPath, attachmentId.toString() + extension + ".preview");
    }

    private File getAttachmentFile(
            SignalServiceAttachmentRemoteId attachmentId, Optional<String> filename, Optional<String> contentType
    ) {
        final var extension = getAttachmentExtension(filename, contentType);
        return new File(attachmentsPath, attachmentId.toString() + extension);
    }

    private static String getAttachmentExtension(
            final Optional<String> filename, final Optional<String> contentType
    ) {
        return filename.filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".") + 1))
                .or(() -> contentType.flatMap(MimeUtils::guessExtensionFromMimeType))
                .map(ext -> "." + ext)
                .orElse("");
    }

    private void createAttachmentsDir() throws IOException {
        IOUtils.createPrivateDirectories(attachmentsPath);
    }

    @FunctionalInterface
    public interface AttachmentStorer {

        void store(OutputStream outputStream) throws IOException;
    }
}
