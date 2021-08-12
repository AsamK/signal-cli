package org.asamk.signal.manager.util;

import org.asamk.signal.manager.AttachmentInvalidException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AttachmentUtils {

    public static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> signalServiceAttachments = null;
        if (attachments != null) {
            signalServiceAttachments = new ArrayList<>(attachments.size());
            for (var attachment : attachments) {
                try {
                    signalServiceAttachments.add(createAttachment(new File(attachment)));
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }
        }
        return signalServiceAttachments;
    }

    public static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
        final var streamDetails = Utils.createStreamDetailsFromFile(attachmentFile);
        return createAttachment(streamDetails, Optional.of(attachmentFile.getName()));
    }

    public static SignalServiceAttachmentStream createAttachment(
            StreamDetails streamDetails, Optional<String> name
    ) {
        // TODO mabybe add a parameter to set the voiceNote, borderless, preview, width, height and caption option
        final var uploadTimestamp = System.currentTimeMillis();
        Optional<byte[]> preview = Optional.absent();
        Optional<String> caption = Optional.absent();
        Optional<String> blurHash = Optional.absent();
        final Optional<ResumableUploadSpec> resumableUploadSpec = Optional.absent();
        return new SignalServiceAttachmentStream(streamDetails.getStream(),
                streamDetails.getContentType(),
                streamDetails.getLength(),
                name,
                false,
                false,
                false,
                preview,
                0,
                0,
                uploadTimestamp,
                caption,
                blurHash,
                null,
                null,
                resumableUploadSpec);
    }
}
