package org.asamk.signal.socket.json;

public class JsonSendMessageResponse extends JsonResponse {
	private final Long timestamp;

	public JsonSendMessageResponse(final JsonEnvelope req, final StatusCode code, final Long timestamp) {
		super(req, code);
		this.timestamp = timestamp;
	}

	public Long getTimestamp() {
		return timestamp;
	}
}
