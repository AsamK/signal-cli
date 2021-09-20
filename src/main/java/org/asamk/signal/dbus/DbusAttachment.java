package org.asamk.signal.dbus;

import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.util.Utils;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public final class DbusAttachment extends Struct
{
  @Position(0)
  private String contentType;
  @Position(1)
  private String fileName;
  @Position(2)
  private String id;
  @Position(3)
  private Long size;
  @Position(4)
  private Integer keyLength;
  @Position(5)
  private boolean voiceNote;
  @Position(6)
  private Integer width;
  @Position(7)
  private Integer height;
  @Position(8)
  private String caption;
  @Position(9)
  private String blurHash;

/*
 * API = 2.15.3 from https://github.com/Turasa/libsignal-service-java (nonstandard)
  public SignalServiceAttachmentStream(InputStream inputStream,
                                       String contentType,
                                       long length,
                                       Optional<String> fileName,
                                       boolean voiceNote,
                                       boolean borderless,
                                       boolean gif, //nonstandard
                                       Optional<byte[]> preview,
                                       int width,
                                       int height,
                                       long uploadTimestamp,
                                       Optional<String> caption,
                                       Optional<String> blurHash,
                                       ProgressListener listener, //Android OS
                                       CancellationSignal cancellationSignal, //Android OS, Signal developers misspelled class name
                                       Optional<ResumableUploadSpec> resumableUploadSpec)


  public SignalServiceAttachmentPointer(int cdnNumber,
                                       SignalServiceAttachmentRemoteId remoteId,
                                       String contentType,
                                       byte[] key,
                                       Optional<Integer> size,
                                       Optional<byte[]> preview,
                                       int width,
                                       int height,
                                       Optional<byte[]> digest,
                                       Optional<String> fileName,
                                       boolean voiceNote,
                                       boolean borderless,
                                       Optional<String> caption,
                                       Optional<String> blurHash,
                                       long uploadTimestamp)

other stuff :
  private long              id;        // used by v2 attachments, see note
  private int               keyLength; //TODO: if you're going to do that, probably should have previewLength and digestLength

notes :
"size" appears to be the same as "length" but is int rather than long
"length" represents file size (or stream/attachment size)
"preview" is also known as "thumbnail"

from SignalServiceAttachmentRemoteId.java :
 * Represents a signal service attachment identifier. This can be either a CDN key or a long, but
 * not both at once. Attachments V2 used a long as an attachment identifier. This lacks sufficient
 * entropy to reduce the likelihood of any two uploads going to the same location within a 30-day
 * window. Attachments V3 uses an opaque string as an attachment identifier which provides more
 * flexibility in the amount of entropy present.

 */

    public DbusAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();

        if (attachment.isPointer()) {
            final var pointer = attachment.asPointer();
            this.id = pointer.getRemoteId().toString();
            this.fileName = pointer.getFileName().orNull();
            if (this.fileName == null) {
                this.fileName = "";
            }
            this.size = pointer.getSize().transform(Integer::longValue).orNull();
            if (this.size == null) {
                this.size = 0L;
            }
            this.setKeyLength(pointer.getKey().length);
            this.setWidth(pointer.getWidth());
            this.setHeight(pointer.getHeight());
            this.setVoiceNote(pointer.getVoiceNote());
            if (pointer.getCaption().isPresent()) {
                this.setCaption(pointer.getCaption().get());
            } else {
                this.setCaption("");
            }
            this.setBlurHash("");
        } else {
            final var stream = attachment.asStream();
            this.fileName = stream.getFileName().orNull();
            if (this.fileName == null) {
                this.fileName = "";
            }
            this.id = "";
            this.size = stream.getLength();
            this.setKeyLength(0);
            this.setWidth(0);
            this.setHeight(0);
            this.setVoiceNote(false);
            this.setCaption("");
            this.setBlurHash("");
        }
    }

    public DbusAttachment(String fileName) throws UserErrorException {
        this.contentType = "application/octet-stream";
        try {
            final File file = new File(fileName);
            this.contentType = Utils.getFileMimeType(file, "application/octet-stream");
            this.size = file.length();
        } catch (IOException e) {
            //no such file, try URL
            try {
                final URL aURL = new URL(fileName);
                this.contentType = aURL.openConnection().getContentType();
                this.size = aURL.openConnection().getContentLengthLong();
            } catch (IOException f) {
                throw new UserErrorException("Cannot find attachment " + fileName + ". " + f.getMessage());
            }
        }
        this.fileName = fileName;
        this.id = "";
        this.setKeyLength(0);
        this.setWidth(0);
        this.setHeight(0);
        this.setVoiceNote(false);
        this.setCaption("");
        this.setBlurHash("");
   }

    public String getContentType() {
        return contentType;
    }
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return size;
    }
    public void setFileSize(Long size) {
        this.size = size;
    }

    public Integer getKeyLength() {
        return keyLength;
    }
    public void setKeyLength(Integer keyLength) {
        this.keyLength = keyLength;
    }

    public Integer getWidth() {
        return width;
    }
    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }
    public void setHeight(Integer height) {
        this.height = height;
    }

    public boolean isVoiceNote() {
        return voiceNote;
    }
    public boolean getVoiceNote() {
        return voiceNote;
    }
    public void setVoiceNote(boolean voiceNote) {
        this.voiceNote = voiceNote;
    }

    public String getCaption() {
        return caption;
    }
    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getBlurHash() {
        return blurHash;
    }
    public void setBlurHash(String blurHash) {
        this.blurHash = blurHash;
    }

}

