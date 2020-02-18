package org.asamk.signal.commands;

import org.asamk.signal.JsonMessageEnvelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;


@JsonInclude(Include.NON_NULL)
public class JsonEventLoopStatusReport {

	public static final int CURRENT_APIVER = 2;
	
	// statusCode constants
	public static final int STATUSCODE_UNKNOWN = -1;
	public static final int STATUSCODE_OK = 0;
	public static final int STATUSCODE_GENERIC_ERROR = 1;
	public static final int STATUSCODE_SEND_ERROR = 2;
	public static final int STATUSCODE_GROUP_ID_FORMAT_ERROR = 3;
	public static final int STATUSCODE_RECIPIENT_FORMAT_ERROR = 4;
	public static final int STATUSCODE_REQUEST_FORMAT_ERROR = 5;
	public static final int STATUSCODE_JSON_PARSE_ERROR = 6;	
	public static final int STATUSCODE_USER_NOT_REGISTERED_ERROR = 7;	
	
	// members
	@JsonInclude(Include.ALWAYS)
	public String respType;
	@JsonInclude(Include.ALWAYS)
	public JsonNode reqID;
	@JsonInclude(Include.ALWAYS)
	public int statusCode = STATUSCODE_UNKNOWN;
	@JsonInclude(Include.NON_NULL)
	public String errorMessage;
	@JsonInclude(Include.NON_NULL)
	public JsonMessageEnvelope envelope;
	@JsonInclude(Include.NON_NULL)
	public JsonNode data;
	@JsonInclude(Include.NON_DEFAULT)
	public int apiVer = 0;
	@JsonInclude(Include.NON_NULL)
	public String attachmentsPath;


	/**
	 * 		Create new JsonEventLoopStatusReport object for use for responding signal data envelopes
	 * 
	 * @param en Envelope from signal Manager
	 */
	public JsonEventLoopStatusReport( JsonMessageEnvelope en) {
		this.envelope = en;
		this.respType = "envelope";
		this.statusCode = STATUSCODE_OK;
		this.reqID = null;
	}
	
	public JsonEventLoopStatusReport( String respType, JsonNode reqID, JsonNode data) {
		this.respType = respType;
		this.statusCode = 0;
		this.reqID = reqID;
		this.data = data;
	}
	
	/**
	 * 		Creates new JsonEventLoopStatusReport object to use for responding to requests
	 * 
	 * @param respType Response type (should correspond to request type somehow)
	 * @param reqID Request ID object as present in the request
	 * @param status Status of the operation, should be "ok" or "error"
	 */
	public JsonEventLoopStatusReport( String respType, JsonNode reqID, int statusCode) {
		this.respType = respType;
		this.reqID = reqID;
		this.statusCode = statusCode;
	}

	/**
	 * 		Creates new JsonEventLoopStatusReport object to use for responding to requests
	 * 
	 * @param respType Response type (should correspond to request type somehow)
	 * @param reqID Request ID object as present in the request
	 * @param status Status of the operation, should be "ok" or "error"
	 * @param message Message explaining what went wrong in case of status="error"
	 */
	public JsonEventLoopStatusReport( String respType, JsonNode reqID, int statusCode, String message) {
		this.respType = respType;
		this.reqID = reqID;
		this.statusCode = statusCode;
		this.errorMessage = message;
	}

	/**
	 * 		Serializes object and output JSON text to System.out
	 */
	public void emit() {
		ObjectMapper mpr = new ObjectMapper();
		mpr.setVisibility( PropertyAccessor.FIELD, Visibility.ANY);
		try {
			//System.out.println( mpr.writeValueAsString(this));
			JsonNode n = mpr.valueToTree(this);
			System.out.println( mpr.writeValueAsString(n));
		} catch( IllegalArgumentException e) {
			System.err.println( "JsonEventLoopStatusReport: ERROR: Failed to serialize object: " + e.toString());
		} catch( JsonProcessingException e) {
			System.err.println( "JsonEventLoopStatusReport: ERROR: Failed to serialize object: " + e.toString());
		}
	}
	
}
