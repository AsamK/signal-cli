package org.asamk.signal.manager.util;

import org.asamk.signal.manager.api.AttachmentInvalidException;
import org.signal.protos.resumableuploads.ResumableUpload;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public class AttachmentUtils {

    public static SignalServiceAttachmentStream createAttachmentStream(
            String attachment, ResumableUpload resumableUpload
    ) throws AttachmentInvalidException {
        try {
            final var streamDetails = Utils.createStreamDetails(attachment);

            return createAttachmentStream(streamDetails.first(), streamDetails.second(), resumableUpload);
        } catch (IOException e) {
            throw new AttachmentInvalidException(attachment, e);
        }
    }

    public static SignalServiceAttachmentStream createAttachmentStream(
            StreamDetails streamDetails, Optional<String> name, ResumableUpload resumableUpload
    ) throws ResumeLocationInvalidException {
        // TODO maybe add a parameter to set the voiceNote, borderless, preview, width, height and caption option
        final var uploadTimestamp = System.currentTimeMillis();
        final var resumableUploadSpec = ResumableUploadSpec.from(resumableUpload);
        return SignalServiceAttachmentStream.newStreamBuilder()
                .withStream(streamDetails.getStream())
                .withContentType(streamDetails.getContentType())
                .withLength(streamDetails.getLength())
                .withFileName(name.orElse(null))
                .withUploadTimestamp(uploadTimestamp)
                .withResumableUploadSpec(resumableUploadSpec)
                .withUuid(UUID.randomUUID())
                .build();
    }
}
