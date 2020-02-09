package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.GroupIdFormatException;
import org.asamk.signal.JsonEvtLoopReceiveMessageHandler;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.asamk.signal.util.Util;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.util.Base64;

import com.fasterxml.jackson.databind.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;



public class JsonEvtLoopCommand implements LocalCommand {


	
    /**
     *  Handles the rows read from STDIN (parse JSON and act on the requests)
     * 	@author bingel
     *
     */
    private static class JsonEvtLoopRequestHandler {
    	Manager m;

    	/**
    	 * Constructor
    	 * @param m Signal Manager object
    	 */
    	JsonEvtLoopRequestHandler( Manager m) {    		
    		this.m = m;
    	}

    	/**
    	 * 	Send signal message and respond with status message
    	 * @param reqObj JSON parsed request object
    	 */
    	JsonEvtLoopStatusReport sendMessage( JsonNode reqObj, JsonNode reqID) {

    		// get body text
    		String body_text = null;
    		if(reqObj.get("messageBody") != null) {
    			body_text = reqObj.get("messageBody").asText();
    		}
    		
    		// parse attachment list
    		List<String> attachments = new ArrayList<String>();
    		if(reqObj.get("attachments") != null && reqObj.get("attachments").isArray()) {
    			for( JsonNode node : reqObj.get("attachments")) {
    				attachments.add(node.asText());
    			}
    		}
    		
    		if( reqObj.get("recipientNumber") != null) {
    			// send message directly to another user
    			String recipientNumber = reqObj.get("recipientNumber").asText();
    			try {
    				m.sendMessage( body_text, attachments, recipientNumber);    			
    			} catch( IOException e) {
    				return new JsonEvtLoopStatusReport( "send_message", reqID, "error", "IOException: " + e.toString() );
    			} catch( EncapsulatedExceptions e) {
    				return new JsonEvtLoopStatusReport( "send_message", reqID, "error", "EncapsulatedException: " + e.toString() );    				
    			}
    		} else if( reqObj.get("recipientGroupID") != null) {
    			// send message to group
    			String recipientGroupID = reqObj.get("recipientGroupID").asText();
    			try {
    				byte[] recipientGroupID_bytearray = Util.decodeGroupId(recipientGroupID);
    				m.sendGroupMessage( body_text, attachments, recipientGroupID_bytearray);
    			} catch( GroupIdFormatException e) {
    				return new JsonEvtLoopStatusReport( "send_message", reqID, "error", "GroupIdFormatException: " + e.toString() );    				
    			} catch( IOException e) {
    				return new JsonEvtLoopStatusReport( "send_message", reqID, "error", "IOException: " + e.toString() );    				
    			} catch( EncapsulatedExceptions e) {
    				return new JsonEvtLoopStatusReport( "send_message", reqID, "error", "EncapsulatedException: " + e.toString() );    				
    			}
    		} else {
    			return new JsonEvtLoopStatusReport( "send_message", reqID, "error", "Neither recipientNumber or recipientGroupID present in request, nothing to do");
    		}
    		
    		return new JsonEvtLoopStatusReport("send_message", reqID, "ok");
    	}

    	/**
    	 * 	Handle incoming textRow from stdin, parse as JSON and dispatch request 
    	 * @param textRow String with the textRow to process
    	 */
    	void handle( String textRow) {
    		System.err.println( "JsonRequestHandler: incoming string: " + textRow);
    		
    		JsonEvtLoopStatusReport resp = null;
    		boolean exitNow = false;
    		try {
    			ObjectMapper objectMapper = new ObjectMapper();
    			JsonNode reqObj = objectMapper.readTree(textRow);
    			if(reqObj != null) {
    				if( reqObj.get("reqType") != null) {
	    				String reqType = reqObj.get("reqType").asText();
	    				JsonNode reqID = reqObj.get("reqID");
	    				switch(reqType) {
	    				case "alive":
	    					resp = new JsonEvtLoopStatusReport("alive", reqID, "ok");
	    					break;
	    				case "exit":
	    					resp = new JsonEvtLoopStatusReport("exit", reqID, "ok");
	    					//System.exit(0);
	    					exitNow = true;
	    					break;
	    				case "send_message":
	    					resp = this.sendMessage( reqObj, reqID);
	    					break;
	    				default:
	    					resp = new JsonEvtLoopStatusReport("error", reqID, "error", "Unknown reqType '" + reqType + "'");
	    					System.err.println("JsonEvtRequestHandler: ERROR: Unknown reqType '" + reqType + "'");
	    					break;
	    				}
    				} else {
    					resp = new JsonEvtLoopStatusReport("error", null, "error", "reqType attribute missing");
    					System.err.println("JsonEvtRequestHandler: ERROR: reqType attribute is missing in request");    					
    				}
    			} else {
					resp = new JsonEvtLoopStatusReport("error", null, "error", "Failed to parse JSON, reqObj is NULL");
        			System.err.println( "JsonEvtRequestHandler: Failed to parse JSON, reqObj is NULL");			
    			}
    		} catch( IOException e) {
				resp = new JsonEvtLoopStatusReport("error", null, "error", "Failed to parse JSON: " + e.toString());
    			System.err.println( "JsonEvtRequestHandler: Failed to parse JSON, text='" + textRow + "', IOException: " + e.toString());
    		}

    		// Emit response (if any - but there should always be one, otherwise something is wrong)
    		if(resp != null) {
    			resp.emit();
    		} else {
    			System.err.println( "JsonEvtRequestHandler: ERROR: No response!");    			
    		}
    		
    		// Someone requested we exit the program now
    		if(exitNow) {
    			System.exit(0);
    		}
    	}
    }
    
	/**
	 * Thread Runner to handle reading STDIN line-by-line and passing to JsonEvtRequestHandler
	 * @author bingel
	 *
	 */
	private static class JsonStdinReader implements Runnable {
	    JsonEvtLoopRequestHandler jsonEvtLoopRequestHandler = null;
	
	    public void run() {
	        BufferedReader br = new BufferedReader( new InputStreamReader( System.in));
	        while( true) {
	            String line=null;
	            try {
	                line = br.readLine();
	            } catch( IOException e) {
	                System.err.println("ERROR Reading stdin: " + e.toString() + ", exiting");
	                System.exit(1);
	            }
	            if( line != null && !line.equals(""))
	                this.jsonEvtLoopRequestHandler.handle(line);
	        }
	    }
	
	    public JsonStdinReader( JsonEvtLoopRequestHandler j) {
	        this.jsonEvtLoopRequestHandler = j;
	    }
	}

	
	
	
	
	
    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            //System.err.println("User is not registered.");
        	new JsonEvtLoopStatusReport( "error", null, "error", "JsonEvtLoopCommand::handleCommand: User is not registered, aborting");
            return 1;
        }

        // Start stdin reader thread
        JsonEvtLoopRequestHandler jhandler = new JsonEvtLoopRequestHandler( m);
        JsonStdinReader reader = new JsonStdinReader( jhandler);
        Thread jsonStdinReaderThread = new Thread( reader);
        jsonStdinReaderThread.start();

        // start JsonReceiveMessageHandler and let it run forever
        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
        try {
        	final Manager.ReceiveMessageHandler handler = new JsonEvtLoopReceiveMessageHandler(m, (resp) -> {
        		// For future use - utilize this lambda callback to emit responses in correct channel
        		// if that should not happen to be stdout, but for now, just emit json text to stdout
        		resp.emit();
        	});
            m.receiveMessages((long) (3600 * 1000), TimeUnit.MILLISECONDS, false, ignoreAttachments, handler);
            return 0;
        } catch (IOException e) {
            System.err.println("Error while receiving messages: " + e.getMessage());
            new JsonEvtLoopStatusReport( "error", null, "error", "JsonEvtLoopCommand::handleCommand: Error while receiving messages: " + e.getMessage()).emit();
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        }
    }
    

    
}
