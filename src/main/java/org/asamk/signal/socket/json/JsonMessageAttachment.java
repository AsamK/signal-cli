package org.asamk.signal.socket.json;

import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class JsonMessageAttachment {
	@JsonIgnore
	private byte[] data;
	private String filename;
	private String base64Data;

	public String getFilename() {
		return filename;
	}

	public void setFilename(final String filename) {
		this.filename = filename;
	}

	public String getBase64Data() {
		return base64Data;
	}

	public void setBase64Data(final String base64Data) {
		this.base64Data = base64Data;
		data = base64Data != null ? Base64.getDecoder().decode(base64Data) : null;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(final byte[] data) {
		this.data = data;
		this.base64Data = data != null ? Base64.getEncoder().encodeToString(data) : null;
	}
}
