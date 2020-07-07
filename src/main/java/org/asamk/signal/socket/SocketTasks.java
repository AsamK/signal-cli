package org.asamk.signal.socket;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.manager.Manager;
import org.asamk.signal.socket.Commander;

public class SocketTasks implements Runnable {
    private String address;
    private int port;
    private Socket socket;
    private OutputStream output = null;
    private ObjectMapper mapper;
    private Commander commander;

    public SocketTasks(Namespace namespace, Manager manager) {
        this.address = namespace.getString("address");
        this.port = namespace.getInt("port");
        this.mapper = new ObjectMapper();
        this.commander = new Commander(manager, this);
    }

    public void send(String message) {
        if (message == null) return;
        if (this.output == null || this.socket.isClosed()) {
            System.err.println("WARNING: a message is ready but the socket isn't connected");
            return;
        }
        message += "\n";
        try {
            this.output.write(message.getBytes(Charset.forName("UTF-8")));
            this.output.flush();
        } catch (SocketException e) {
            System.err.println("Output socket connection lost!");
            this.output = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                ServerSocket serverSocket = new ServerSocket(this.port, 1, InetAddress.getByName(this.address));
                System.out.println("Socket ready, binding to address: " + this.address + ":" + this.port);
                this.socket = serverSocket.accept(); // accept() blocks
                this.output = socket.getOutputStream();
                InputStream input = socket.getInputStream();

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input));
                System.out.println("Connection established.");
                while (this.responseProcessor(bufferedReader.readLine())); // readLine() blocks

                bufferedReader.close();
                this.output.close();
                input.close();
                this.socket.close();
                serverSocket.close();
            } catch (Exception e) { System.err.println(e); }
                System.err.println("Socket connection lost.");
            try {
                // Help relax potential resource hogging and log spam
                TimeUnit.SECONDS.sleep(3);
            } catch (Exception e) {}
        }
    }

    private boolean responseProcessor(String line) {
        if (line == null) return false;
        JsonNode node = null;
        try {
            node = this.mapper.readValue(line.trim(), JsonNode.class);
        } catch (Exception e) { System.err.println(e); }
        if (node == null) return true;
        String reply = null;
        Iterator<Map.Entry<String,JsonNode>> entryIt = node.fields();
        Map.Entry<String,JsonNode> entry;
        String command;
        while (entryIt.hasNext()) {
            entry = entryIt.next();
            command = entry.getKey();
            this.commander.handleCommand(command, entry.getValue());
        }
        return true;
    }

}
