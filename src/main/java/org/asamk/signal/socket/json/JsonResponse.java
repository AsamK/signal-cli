package org.asamk.signal.socket.json;

public class JsonResponse extends JsonEnvelope {
	private final StatusCode statusCode;

	public static enum StatusCode {
		UNKNOWN(-1),
		SUCCESS(0),
		UNKNOWN_COMMAND(1),
		INVALID_NUMBER(2),
		INVALID_ATTACHMENT(3),
		INVALID_JSON(4),
		INVALID_RECIPIENT(5),
		GROUP_NOT_FOUND(6),
		NOT_A_GROUP_MEMBER(7),
		MESSAGE_ERROR(8),
		MISSING_MESSAGE_CONTENT(9),
		MESSAGE_PARSING_ERROR(10),
		MISSING_REACTION(11);

		private int code;

		private StatusCode(final int c) {
			code = c;
		}

		public int getCode() {
			return code;
		}
	}

	public JsonResponse(final JsonEnvelope req, final StatusCode code) {
		if (req != null) {
			setReqId(req.getReqId());
			setCommand(req.getCommand());
		}
		statusCode = code != null ? code : StatusCode.UNKNOWN;
	}

	public int getStatusCode() {
		return statusCode.getCode();
	}
}
