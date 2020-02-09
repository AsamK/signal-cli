package org.asamk.signal;

import java.io.File;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

public class JsonAttachment {

    String contentType;
    String filename;
    String id;
    String storagePath;
    int size;

    // This is a bit of a kludge, but the alternative of getting a reference
    // to Manager (who knows about the attachmentPath) from somewhere was even worse.
    static private String attachmentStoragePath = "";

    static public String getAttachmentStoragePath() {
    	return attachmentStoragePath;
    }
    
    static public void setAttachmentStoragePath( String p) {
    	attachmentStoragePath = p;
    }
    
    JsonAttachment(SignalServiceAttachment attachment) {
        this.contentType = attachment.getContentType();

        final SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (attachment.isPointer()) {
            this.id = String.valueOf(pointer.getId());
            if (pointer.getFileName().isPresent()) {
                this.filename = pointer.getFileName().get();
            }
            if (pointer.getSize().isPresent()) {
                this.size = pointer.getSize().get();
            }
            this.storagePath = attachmentStoragePath + File.separator + this.id;
        }
    }
}
