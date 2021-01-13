package org.asamk.signal.socket.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class JsonErrorResponse extends JsonResponse implements IJsonReceiveableMessage {
	@JsonInclude(Include.NON_EMPTY)
	private final String errorMessage;

	public JsonErrorResponse(final StatusCode code, final String errorMsg) {
		this(null, code, errorMsg);
	}

	public JsonErrorResponse(final JsonEnvelope req, final StatusCode code, final String errorMsg) {
		super(req, code);
		this.errorMessage = errorMsg;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}
