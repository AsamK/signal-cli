package org.asamk.signal.socket.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

public class JsonEnvelope {
	@JsonInclude(Include.NON_EMPTY)
	private String command;
	@JsonInclude(Include.NON_EMPTY)
	private String reqId;

	public String getCommand() {
		return command;
	}

	public void setCommand(final String command) {
		this.command = command;
	}

	public String getReqId() {
		return reqId;
	}

	public void setReqId(final String reqId) {
		this.reqId = reqId;
	}
}
