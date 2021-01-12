package org.asamk.signal.manager.util;

import org.asamk.signal.manager.AttachmentInvalidException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AttachmentUtils {

    public static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> signalServiceAttachments = null;
        if (attachments != null) {
            signalServiceAttachments = new ArrayList<>(attachments.size());
            for (String attachment : attachments) {
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
        InputStream attachmentStream = new FileInputStream(attachmentFile);
        final long attachmentSize = attachmentFile.length();
        final String mime = Utils.getFileMimeType(attachmentFile, "application/octet-stream");
        // TODO mabybe add a parameter to set the voiceNote, borderless, preview, width, height and caption option
        final long uploadTimestamp = System.currentTimeMillis();
        Optional<byte[]> preview = Optional.absent();
        Optional<String> caption = Optional.absent();
        Optional<String> blurHash = Optional.absent();
        final Optional<ResumableUploadSpec> resumableUploadSpec = Optional.absent();
        return new SignalServiceAttachmentStream(attachmentStream,
                mime,
                attachmentSize,
                Optional.of(attachmentFile.getName()),
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

    public static File retrieveAttachment(SignalServiceAttachmentStream stream, File outputFile) throws IOException {
        InputStream input = stream.getInputStream();

        try (OutputStream output = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return outputFile;
    }
}
