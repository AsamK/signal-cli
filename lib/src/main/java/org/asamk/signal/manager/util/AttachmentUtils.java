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
import java.net.URL;

public class AttachmentUtils {

    public static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> signalServiceAttachments = null;
        if (attachments != null) {
            signalServiceAttachments = new ArrayList<>(attachments.size());
            for (var attachment : attachments) {
                try {
                    signalServiceAttachments.add(createAttachment(new File(attachment)));
                } catch (IOException f) {
                    // no such file, send it as URL
                    try {
                        signalServiceAttachments.add(createAttachment(new URL(attachment)));
                    } catch (IOException e) {
                        throw new AttachmentInvalidException(attachment, e);
                    }
                }
            }
        }
        return signalServiceAttachments;
    }

    public static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
        final var streamDetails = Utils.createStreamDetailsFromFile(attachmentFile);
        return createAttachment(streamDetails, Optional.of(attachmentFile.getName()));
    }

    public static SignalServiceAttachmentStream createAttachment(URL aURL) throws IOException {
        final var streamDetails = Utils.createStreamDetailsFromURL(aURL);
        String path = aURL.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return createAttachment(streamDetails, Optional.of(name));
    }

    public static SignalServiceAttachmentStream createAttachment(
            StreamDetails streamDetails, Optional<String> name
    ) {
        // TODO maybe add a parameter to set the voiceNote, borderless, preview, width, height, caption, blurHash options
        final var uploadTimestamp = System.currentTimeMillis();
        boolean voicenote = false;
        boolean borderless = false;
        Optional<byte[]> preview = Optional.absent();
        int width = 0;
        int height = 0;
        Optional<String> caption = Optional.absent();
        Optional<String> blurHash = Optional.absent();
        final Optional<ResumableUploadSpec> resumableUploadSpec = Optional.absent();
        //ProgressListener listener = null; //Android OS
        //CancellationSignal cancellationSignal = null; //Android OS; Signal developers misspelled class name

        return new SignalServiceAttachmentStream(streamDetails.getStream(),
                streamDetails.getContentType(),
                streamDetails.getLength(),
                name,
                voicenote,
                borderless,
                false,
                preview,
                width,
                height,
                uploadTimestamp,
                caption,
                blurHash,
                null,
                null,
                resumableUploadSpec);
    }
}
