package org.asamk.signal.socket.json;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class JsonReceivedMessage extends JsonResponse implements IJsonReceiveableMessage {
	private final Long timestamp;
	private final String sender;
	@JsonInclude(Include.NON_EMPTY)
	private String body;
	private final List<JsonMessageAttachment> attachments;

	@JsonInclude(Include.NON_EMPTY)
	private String groupId;

	public JsonReceivedMessage(final Long timestamp, final String sender) {
		super(null, StatusCode.SUCCESS);
		this.timestamp = timestamp;
		this.sender = sender;
		this.attachments = new ArrayList<>();
	}

	public JsonReceivedMessage withBody(final String body) {
		this.body = body;
		return this;
	}

	public JsonReceivedMessage withGroupId(final String groupId) {
		this.groupId = groupId;
		return this;
	}

	public JsonReceivedMessage withAttachment(final JsonMessageAttachment attach) {
		attachments.add(attach);
		return this;
	}

	public String getSender() {
		return sender;
	}

	public String getBody() {
		return body;
	}

	public String getGroupId() {
		return groupId;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public List<JsonMessageAttachment> getAttachments() {
		return attachments;
	}
}
