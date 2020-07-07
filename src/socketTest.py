#!/usr/bin/python3
import socket
import time
import sys
import signal # optional: for SIGINT, SIGKILL
import json

class SignalProcessor:
    def __init__(self):
        self.HOST = '127.0.0.1'
        self.PORT = 24250
        self.messageQueue = []

    def connect(self):
        print(f'Connecting to {self.HOST}:{self.PORT}...')
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        while True:
            try:
                self.sock.connect((self.HOST, self.PORT))
                self.sock.setblocking(False) # sock.settimeout(0.0)
                break
            except ConnectionRefusedError:
                print('Refused, attemping to reconnect...')
                time.sleep(3)
        print('Connection established')
        self.testCommands()

    def receive(self):
        try:
            buffer = self.sock.recv(65536) # should be plenty for large contact lists
        except BlockingIOError:
            return True
        if not buffer:
            print('Lost connection!')
            return False
        for line in filter(None, buffer.split(b'\n')):
            self.messageQueue.append(line.decode())
        return True

    def send(self, message):
        self.sock.sendall(message.encode() + b'\n');

    def testCommands(self):
        #self.send('{ "sendMessage" : { "contacts" : [ "+31638555555" ], "groups" : [ "Y5555rtl2p/TnLYvY555dA==", "DK555555UjPU55545557bA==" ], "message" : "This GROUP message comes from Python!" } }')
        #self.send('{ "updateContacts" : { "+31638555555" : { "archived" : false } }, "getContacts" : "" }')
        #self.send('{ "sendMessage" : { "contacts" : [ "+31638555555" ], "message" : "This PM comes from Python <3" } }')
        #self.send('{ "getGroups" : "", "getContacts" : "" }')
        self.send('{ "trust" : { "contacts" : [ "+31638555555" ] }, "endSession" : { "contacts" : [ "+31638555555" ] } }')
        time.sleep(1)

    def handleMessages(self):
        while len(signalCli.messageQueue):
            rawMessage = signalCli.messageQueue.pop(0)
            print(rawMessage)
            obj = json.loads(rawMessage)
            try:
                source = obj['envelope']['source']
                msg = obj['envelope']['dataMessage']['message']
            except Exception:
                continue
            if msg.strip().lower() == 'love':
                replyObj = { 'sendMessage': { 'contacts' : source.split(), 'message' : 'From Russia with ' + msg } }
                self.send(json.dumps(replyObj))

    def yourTimedCode(self):
        print('yourStuff')

def sigHandler(signal, frame):
    print(f'Clean exit, received signal {signal}')
    sys.exit(0)

if __name__ == '__main__':
    signal.signal(signal.SIGINT, sigHandler)
    signal.signal(signal.SIGTERM, sigHandler)
    signalCli = SignalProcessor()
    signalCli.connect()
    while True:
        if signalCli.receive():
            signalCli.handleMessages()
            signalCli.yourTimedCode()
            time.sleep(1)
        else:
            signalCli.connect()

# EOF
