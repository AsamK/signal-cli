package org.asamk.signal;

/*
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
*/

//import org.asamk.signal.*;
import org.asamk.signal.commands.*;
import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

//import java.io.IOException;

public class JsonEventLoopReceiveMessageHandler implements Manager.ReceiveMessageHandler {

	public interface ResponseEmitter {
		public void emit( JsonEventLoopStatusReport resp);
	}
	
    final Manager m;
    ResponseEmitter responseEmitter;

    public JsonEventLoopReceiveMessageHandler(Manager m) {
        this.m = m;
    }
    
    public JsonEventLoopReceiveMessageHandler( Manager m, ResponseEmitter responseEmitter) {
    	this.m = m;
    	this.responseEmitter = responseEmitter;
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        //ObjectNode result = jsonProcessor.createObjectNode();
    	JsonEventLoopStatusReport resp = null;
        if (exception != null) {
        	resp = new JsonEventLoopStatusReport( "error", null, JsonEventLoopStatusReport.STATUSCODE_GENERIC_ERROR, "JsonEventLoopReceiveMessageHandler::handleMessage: Exception: " + exception.toString());
            //result.putPOJO("error", new JsonError(exception));
        }
        if (envelope != null) {
            //result.putPOJO("envelope", new JsonMessageEnvelope(envelope, content));
        	resp = new JsonEventLoopStatusReport( new JsonMessageEnvelope(envelope, content));
        }
    	if(resp == null) {
    		new JsonEventLoopStatusReport( "error", null, JsonEventLoopStatusReport.STATUSCODE_GENERIC_ERROR, "JsonEventLoopReceiveMessageHandler::handleMessage: both exception and envelope is null!").emit();
    	} else {
    		if( this.responseEmitter != null) {
    			this.responseEmitter.emit( resp);
    		} else {
    			resp.emit();
    		}
    	}
    }
}
