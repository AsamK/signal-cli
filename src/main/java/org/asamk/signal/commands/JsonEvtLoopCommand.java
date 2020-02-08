package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.Signal;
import org.asamk.signal.JsonReceiveMessageHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.DateUtils;
import org.whispersystems.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.util.ErrorUtils.handleAssertionError;



public class JsonEvtLoopCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (!m.isRegistered()) {
            System.err.println("User is not registered.");
            return 1;
        }

        // Start stdin reader thread
        JsonEvtRequestHandler jhandler = new JsonEvtRequestHandler( m);
        JsonStdinReader reader = new JsonStdinReader( jhandler);
        Thread jsonStdinReaderThread = new Thread( reader);
        jsonStdinReaderThread.start();

        boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
        try {
            final Manager.ReceiveMessageHandler handler = new JsonReceiveMessageHandler(m);
            m.receiveMessages((long) (3600 * 1000), TimeUnit.MILLISECONDS, false, ignoreAttachments, handler);
            return 0;
        } catch (IOException e) {
            System.err.println("Error while receiving messages: " + e.getMessage());
            return 3;
        } catch (AssertionError e) {
            handleAssertionError(e);
            return 1;
        }
    }
    
    
    private static class JsonEvtRequestHandler {
    	Manager m;
    	
    	JsonEvtRequestHandler( Manager m) {    		
    		this.m = m;
    	}
    	
    	void handle( String row) {
    		System.err.println( "JsonRequestHandler: incoming string: " + row);
    	}
    }
    
	//
	// 	Thread Runner to handle reading STDIN line-by-line and passing to JsonRequestHandler
	//
	private static class JsonStdinReader implements Runnable {
	    JsonEvtRequestHandler jsonEvtRequestHandler = null;
	
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
	                this.jsonEvtRequestHandler.handle(line);
	        }
	    }
	
	    JsonStdinReader( JsonEvtRequestHandler j) {
	        this.jsonEvtRequestHandler = j;
	    }
	}

    
}
