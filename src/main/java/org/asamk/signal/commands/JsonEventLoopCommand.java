package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.GroupIdFormatException;
import org.asamk.signal.JsonEventLoopReceiveMessageHandler;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.groups.GroupInfo;
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



public class JsonEventLoopCommand implements LocalCommand {


	
    /**
     *  Handles the rows read from STDIN (parse JSON and act on the requests)
     * 	@author bingel
     *
     */
    private static class JsonEventLoopRequestHandler {
    	Manager m;

    	/**
    	 * Constructor
    	 * @param m Signal Manager object
    	 */
    	JsonEventLoopRequestHandler( Manager m) {
    		this.m = m;
    	}

    	/**
    	 * 	Command Handler: list_groups
    	 * @param reqObj
    	 * @param reqID
    	 * @return
    	 */
    	JsonEventLoopStatusReport list_groups( JsonNode reqObj, JsonNode reqID) {
    		List<GroupInfo> groups = m.getGroups();
    		ObjectMapper mapper = new ObjectMapper();
    		JsonNode data_obj = mapper.valueToTree(groups);
    		return new JsonEventLoopStatusReport( "group_list", reqID, data_obj);
    	}
    	
    	/**
    	 * 	Command Handler: list_contacts
    	 */
    	JsonEventLoopStatusReport list_contacts( JsonNode reqObj, JsonNode reqID) {
            List<ContactInfo> contacts = m.getContacts();
            ObjectMapper mpr = new ObjectMapper();
            JsonNode data_obj = mpr.valueToTree(contacts);
            return new JsonEventLoopStatusReport( "contact_list", reqID, data_obj);
    	}
    	
    	/**
    	 * 	Command Handler: Send signal message and respond with status message
    	 * @param reqObj JSON parsed request object
    	 */
    	JsonEventLoopStatusReport send_message( JsonNode reqObj, JsonNode reqID) {

    		JsonNode dataMessage = reqObj.get("dataMessage");
    		if(dataMessage == null) {
    			return new JsonEventLoopStatusReport("send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_REQUEST_FORMAT_ERROR, "send_message: ERROR: dataMessage attribute missing from request object");
    		}
    		
    		// get body text
    		String body_text = null;
    		if(dataMessage.get("message") != null) {
    			body_text = dataMessage.get("message").asText();
    		}
    		
    		// parse attachment list
    		List<String> attachments = new ArrayList<String>();
    		if(dataMessage.get("attachments") != null && dataMessage.get("attachments").isArray()) {
    			for( JsonNode node : dataMessage.get("attachments")) {
    				if( node.get("filename") == null) {
    	    			return new JsonEventLoopStatusReport("send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_REQUEST_FORMAT_ERROR, "send_message: ERROR: 'filename' attribute from attachment object");
    				}
    				attachments.add(node.get("filename").asText());
    			}
    		}
    		
    		if( reqObj.get("recipient") != null) {
    			JsonNode recipient_obj = reqObj.get("recipient");
    			if(recipient_obj.get("number") == null) {
        			return new JsonEventLoopStatusReport("send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_REQUEST_FORMAT_ERROR, "send_message: ERROR: 'recipient' is missing 'number' attribute");    				
    			}
    			// send message directly to another user
    			String recipientNumber = recipient_obj.get("number").asText();
    			try {
    				m.sendMessage( body_text, attachments, recipientNumber);    			
    			} catch( IOException e) {
    				return new JsonEventLoopStatusReport( "send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_SEND_ERROR, "IOException: " + e.toString() );
    			} catch( EncapsulatedExceptions e) {
    				return new JsonEventLoopStatusReport( "send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_SEND_ERROR, "EncapsulatedException: " + e.toString() );    				
    			}
    		} else if( dataMessage.get("groupInfo") != null) {
    			JsonNode groupInfo_obj = dataMessage.get("groupInfo");
    			if(groupInfo_obj.get("groupId") == null) {
        			return new JsonEventLoopStatusReport("send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_REQUEST_FORMAT_ERROR, "send_message: ERROR: dataMessage.groupInfo is missing 'groupId' atribute");
    			}
    			
    			// send message to group
    			String recipientGroupID = groupInfo_obj.get("groupId").asText();
    			try {
    				byte[] recipientGroupID_bytearray = Util.decodeGroupId(recipientGroupID);
    				m.sendGroupMessage( body_text, attachments, recipientGroupID_bytearray);
    			} catch( GroupIdFormatException e) {
    				return new JsonEventLoopStatusReport( "send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_GROUP_ID_FORMAT_ERROR, "GroupIdFormatException: " + e.toString() );    				
    			} catch( IOException e) {
    				return new JsonEventLoopStatusReport( "send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_SEND_ERROR, "IOException: " + e.toString() );
    			} catch( EncapsulatedExceptions e) {
    				return new JsonEventLoopStatusReport( "send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_SEND_ERROR, "EncapsulatedException: " + e.toString() );    				
    			}
    		} else {
    			return new JsonEventLoopStatusReport( "send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_RECIPIENT_FORMAT_ERROR, "Neither recipient.number or dataMessage.groupInfo.groupId present in request");
    		}
    		
    		return new JsonEventLoopStatusReport("send_message", reqID, JsonEventLoopStatusReport.STATUSCODE_OK);
    	}

    	/**
    	 * 	Handle incoming textRow from stdin, parse as JSON and dispatch request 
    	 * @param textRow String with the textRow to process
    	 */
    	void handle( String textRow) {
    		//System.err.println( "JsonRequestHandler: incoming string: " + textRow);
    		
    		JsonEventLoopStatusReport resp = null;
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
	    					resp = new JsonEventLoopStatusReport("alive", reqID, JsonEventLoopStatusReport.STATUSCODE_OK);
	    					break;
	    				case "exit":
	    					resp = new JsonEventLoopStatusReport("exit", reqID, JsonEventLoopStatusReport.STATUSCODE_OK);
	    					//System.exit(0);
	    					exitNow = true;
	    					break;
	    				case "send_message":
	    					resp = this.send_message( reqObj, reqID);
	    					break;
	    				case "list_groups":
	    					resp = this.list_groups( reqObj, reqID);
	    					break;
	    				case "list_contacts":
	    					resp = this.list_contacts( reqObj, reqID);
	    					break;
	    				default:
	    					resp = new JsonEventLoopStatusReport("error", reqID, JsonEventLoopStatusReport.STATUSCODE_REQUEST_FORMAT_ERROR, "Unknown reqType '" + reqType + "'");
	    					System.err.println("JsonEvtRequestHandler: ERROR: Unknown reqType '" + reqType + "'");
	    					break;
	    				}
    				} else {
    					resp = new JsonEventLoopStatusReport("error", null, JsonEventLoopStatusReport.STATUSCODE_REQUEST_FORMAT_ERROR, "reqType attribute missing");
    					System.err.println("JsonEvtRequestHandler: ERROR: reqType attribute is missing in request");    					
    				}
    			} else {
					resp = new JsonEventLoopStatusReport("error", null, JsonEventLoopStatusReport.STATUSCODE_JSON_PARSE_ERROR, "Failed to parse JSON, reqObj is NULL");
        			System.err.println( "JsonEvtRequestHandler: Failed to parse JSON, reqObj is NULL");			
    			}
    		} catch( IOException e) {
				resp = new JsonEventLoopStatusReport("error", null, JsonEventLoopStatusReport.STATUSCODE_JSON_PARSE_ERROR, "Failed to parse JSON: " + e.toString());
    			System.err.println( "JsonEvtRequestHandler: Failed to parse JSON, text='" + textRow + "', IOException: " + e.toString());
    		}

    		// Emit response (if any - but there should always be one, otherwise something is wrong)
    		if(resp != null) {
    			resp.emit();
    		} else {
    			System.err.println( "JsonEventRequestHandler: ERROR: No response!");    			
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
	    JsonEventLoopRequestHandler jsonEventLoopRequestHandler = null;
	
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
	                this.jsonEventLoopRequestHandler.handle(line);
	        }
	    }
	
	    public JsonStdinReader( JsonEventLoopRequestHandler j) {
	        this.jsonEventLoopRequestHandler = j;
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
        	new JsonEventLoopStatusReport( "error", null, JsonEventLoopStatusReport.STATUSCODE_USER_NOT_REGISTERED_ERROR, "JsonEventLoopCommand::handleCommand: User is not registered, aborting");
            return 1;
        }

        // Start stdin reader thread
        JsonEventLoopRequestHandler jhandler = new JsonEventLoopRequestHandler( m);
        JsonStdinReader reader = new JsonStdinReader( jhandler);
        Thread jsonStdinReaderThread = new Thread( reader);
        jsonStdinReaderThread.start();

        // Emit metadata response message at startup
        JsonEventLoopStatusReport meta_resp = new JsonEventLoopStatusReport("metadata", null, JsonEventLoopStatusReport.STATUSCODE_OK);
        meta_resp.apiVer = JsonEventLoopStatusReport.CURRENT_APIVER;
        meta_resp.attachmentsPath = m.getAttachmentsPath();
        meta_resp.emit();
        
        // start JsonReceiveMessageHandler and let it run forever
        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
        try {
        	final Manager.ReceiveMessageHandler handler = new JsonEventLoopReceiveMessageHandler(m, (resp) -> {
        		// For future use - utilize this lambda callback to emit responses in correct channel
        		// if that should not happen to be stdout, but for now, just emit json text to stdout
        		resp.emit();
        	});
            m.receiveMessages((long) (3600 * 1000), TimeUnit.MILLISECONDS, false, ignoreAttachments, handler);
            return 0;
        } catch (IOException e) {
            System.err.println("Error while receiving messages: " + e.getMessage());
            new JsonEventLoopStatusReport( "error", null, JsonEventLoopStatusReport.STATUSCODE_GENERIC_ERROR, "JsonEventLoopCommand::handleCommand: Error while receiving messages: " + e.getMessage()).emit();
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        }
    }
    

    
}
