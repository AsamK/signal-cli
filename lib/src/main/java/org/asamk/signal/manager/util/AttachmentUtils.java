package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttachmentUtils {

    public static List<SignalServiceAttachmentStream> createAttachmentStreams(List<String> attachments) throws AttachmentInvalidException {
        if (attachments == null) {
            return null;
        }
        final var signalServiceAttachments = new ArrayList<SignalServiceAttachmentStream>(attachments.size());
        for (var attachment : attachments) {
            signalServiceAttachments.add(createAttachmentStream(new File(attachment)));
        }
        return signalServiceAttachments;
    }

    public static SignalServiceAttachmentStream createAttachmentStream(File attachmentFile) throws AttachmentInvalidException {
        try {
            final var streamDetails = Utils.createStreamDetailsFromFile(attachmentFile);
            return createAttachmentStream(streamDetails, Optional.of(attachmentFile.getName()));
        } catch (IOException e) {
            throw new AttachmentInvalidException(attachmentFile.toString(), e);
        }
    }

    public static SignalServiceAttachmentStream createAttachmentStream(
            StreamDetails streamDetails, Optional<String> name
    ) {
        // TODO maybe add a parameter to set the voiceNote, borderless, preview, width, height and caption option
        final var uploadTimestamp = System.currentTimeMillis();
        Optional<byte[]> preview = Optional.empty();
        Optional<String> caption = Optional.empty();
        Optional<String> blurHash = Optional.empty();
        final Optional<ResumableUploadSpec> resumableUploadSpec = Optional.empty();
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
