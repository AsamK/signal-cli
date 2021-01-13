package org.asamk.signal.socket.json;

import java.util.List;

public class JsonSendMessageData {
	private String message;
	private List<JsonMessageAttachment> attachments;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<JsonMessageAttachment> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<JsonMessageAttachment> attachments) {
		this.attachments = attachments;
	}
}
