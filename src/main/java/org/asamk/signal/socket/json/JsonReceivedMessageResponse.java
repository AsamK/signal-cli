package org.asamk.signal.socket.json;

import java.util.ArrayList;
import java.util.List;

public class JsonReceivedMessageResponse extends JsonResponse {
	private final List<IJsonReceiveableMessage> messages;

	public JsonReceivedMessageResponse(final JsonEnvelope req) {
		super(req, StatusCode.SUCCESS);
		messages = new ArrayList<>();
	}

	public List<IJsonReceiveableMessage> getMessages() {
		return messages;
	}

	public void addMessage(final IJsonReceiveableMessage msg) {
		if (msg != null) {
			messages.add(msg);
		}
	}
}
