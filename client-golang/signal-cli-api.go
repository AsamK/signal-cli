/**
Accept commands on TCP socket and send to signal-cli daemon on UNIX socket.
Immediate responses are returned via the TCP socket, and other incoming
messages are logged to stdout.

With this program running as a service, we can follow incoming messages with:
	journalctl -fu signal-cli-api --no-tail
**/

package main

import (
	"bufio"
	"fmt"
	"log"
	"net"
	"time"
)

func main() {
	unixSocketPath := "/tmp/signal-cli/socket"
	unixConn, err := net.Dial("unix", unixSocketPath)
	if err != nil {
		log.Fatal(err)
	}
	defer unixConn.Close()

	tcpPort := "5780"
	tcpListener, err := net.Listen("tcp", ":"+tcpPort)
	if err != nil {
		log.Fatal(err)
	}
	defer tcpListener.Close()

	responses := make(chan string)

	// Read messages from the UNIX socket
	go func() {
		scanner := bufio.NewScanner(unixConn)
		for scanner.Scan() {
			resp := scanner.Text()
			if isCommandResponse(resp) {
				responses <- resp
			} else {
				log.Println(resp)
			}
		}
		if err := scanner.Err(); err != nil {
			log.Println(err)
		}
	}()

	for {
		// Wait for TCP connection
		tcpConn, err := tcpListener.Accept()
		if err != nil {
			log.Println(err)
			continue
		}

		// Read command
		cmd, err := bufio.NewReader(tcpConn).ReadString('\n')
		if err != nil {
			log.Println(err)
			tcpConn.Close()
			continue
		}

		// Write command to UNIX socket
		fmt.Fprint(unixConn, cmd)

		// Read responses
		select {
		case msg := <-responses:
			fmt.Fprintln(tcpConn, msg)
		case <-time.After(2 * time.Second):
			fmt.Fprint(tcpConn, "Timed out")
		}

		// Close TCP connection
		tcpConn.Close()
	}
}

/** Check if signal-cli json-rpc message is a command response.
	Command response & error formats:
	  {"jsonrpc":"2.0","result":{...},"id":1678829060000}
	  {"jsonrpc":"2.0","result":[...],"id":1678829060000}
    {"jsonrpc":"2.0","error":{...},"id":1678829060000}
	
  Incoming message format:
	  {"jsonrpc":"2.0","method":"receive","params":{...}}

	This assumes signal-cli doesn't change jsonrpc format or version.
	It would be more robust to parse the JSON, but shouldn't be needed.
**/
func isCommandResponse(resp string) bool {
	return len(resp) >= 26 && resp[:26] != "{\"jsonrpc\":\"2.0\",\"method\":"
}
