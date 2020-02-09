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
public class JsonEvtLoopStatusReport {
	
	public int apiVer = 2;
	public String respType;
	@JsonInclude(Include.ALWAYS)
	public JsonNode reqID;
	public String status;
	public String message;
	public JsonMessageEnvelope envelope;


	/**
	 * 		Create new JsonEvtLoopStatusReport object for use for responding signal data envelopes
	 * 
	 * @param en Envelope from signal Manager
	 */
	public JsonEvtLoopStatusReport( JsonMessageEnvelope en) {
		this.envelope = en;
		this.respType = "envelope";
		this.status = "ok";
		this.reqID = null;
	}
	
	/**
	 * 		Creates new JsonEvtLoopStatusReport object to use for responding to requests
	 * 
	 * @param respType Response type (should correspond to request type somehow)
	 * @param reqID Request ID object as present in the request
	 * @param status Status of the operation, should be "ok" or "error"
	 */
	public JsonEvtLoopStatusReport( String respType, JsonNode reqID, String status) {
		this.respType = respType;
		this.reqID = reqID;
		this.status = status;
	}

	/**
	 * 		Creates new JsonEvtLoopStatusReport object to use for responding to requests
	 * 
	 * @param respType Response type (should correspond to request type somehow)
	 * @param reqID Request ID object as present in the request
	 * @param status Status of the operation, should be "ok" or "error"
	 * @param message Message explaining what went wrong in case of status="error"
	 */
	public JsonEvtLoopStatusReport( String respType, JsonNode reqID, String status, String message) {
		this.respType = respType;
		this.reqID = reqID;
		this.status = status;
		this.message = message;
	}

	/**
	 * 		Serializes object and output JSON text to System.out
	 */
	public void emit() {
		ObjectMapper mpr = new ObjectMapper();
		mpr.setVisibility( PropertyAccessor.FIELD, Visibility.ANY);
		try {
			System.out.println( mpr.writeValueAsString(this));
		} catch( JsonProcessingException e) {
			System.err.println( "JsonEvtLoopStatusReport: ERROR: Failed to serialize object: " + e.toString());
		}
	}
	
}
