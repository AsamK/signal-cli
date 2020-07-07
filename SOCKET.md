# Socket IPC with signal-cli

Signal-cli offers a simple yet powerful way of inter-process communication via sockets. The purpose is to be able to connect your own programmed logic to Signal. Allowing you to manage contacts and their privileges on your own so you can create a powerful automated chat bot with Signal. It is now easy to set up signal-cli in it's own Docker microservice and connect a script to it running in a separate container. This configuration adds security and deployment speed via Docker's network orchestration. There is a [Dockerfile](/Matteljay/signal-cli/blob/master/Dockerfile) included.

## Get started

The API formats are explained below but first, signal-cli should be launched as a socket daemon. As an alternative to Docker, the command below could be wrapped in a [systemd](https://www.shellhacks.com/systemd-service-file-example/) or [screen](https://www.thegeekdiary.com/how-to-use-the-screen-command-in-linux/) launcher script.
It is assumed you have one Signal user registered and verified as explained in the [README](/Matteljay/signal-cli/blob/master/README.md#usage) file. You are now ready to launch the socket listener from the command line:

        signal-cli --singleuser socket

Or by setting the default values explicitly:

        signal-cli -u USERNAME socket -a localhost -p 24250

This will start listening and sending JSON string objects locally on port `24250`. From your own bash or python program, you can now interact seamlessly with the Signal server via this socket. An example of a single-threaded non-blocking testing application can be found here [socketTest.py](/Matteljay/signal-cli/blob/master/src/socketTest.py).

## Sending

```
{
    "sendMessage" : {
        "contacts" : [ "+31638555555" ],
        "message" : "This PM comes from Python <3"
    }
}

{
    "sendMessage" : {
        "groups" : [ "Y5555rtl2p/TnLYvY555dA==", "DK555555UjPU55545557bA==" ],
        "message" : "This GROUP message comes from Python!"
    }
}

{
    "updateContacts" : {
        "+31638555555" : {
            "name" : "NewName",
            "color" : "",
            "messageExpirationTime" : 555000,
            "blocked" : "false",
            "inboxPosition" : 2,
            "archived" : true
        }
    }
}

{
    "trust" : {
        "contacts" : [ "+31638555555" ]
    }
}

{
    "endSession" : {
        "contacts" : [ "+31638555555" ]
    }
}

{
    "getContacts" : ""
}

{
    "getGroups" : ""
}
```

## Receiving

### Incoming message

```
{
    "envelope": {
        "source": "+31638422555",
        "sourceDevice": 1,
        "relay": None,
        "timestamp": 1592692535999,
        "isReceipt": False,
        "dataMessage": {
            "timestamp": 1592692535999,
            "message": "Hello signal-cli!",
            "expiresInSeconds": 0,
            "attachments": [],
            "groupInfo": {
            "groupId": "Y5555rtl2p/TnLYvY555dA==",
            "members": None,
            "name": None,
            "type": "DELIVER"
            }
        },
        "syncMessage": None,
        "callMessage": None,
        "receiptMessage": None
    }
}
```

### Response to 'getContacts'

```
{
    "+31638422555" : {
        "name" : null,
        "uuid" : "1555f555-eb02-555d-a1b6-555517905552",
        "color" : null,
        "messageExpirationTime" : 0,
        "profileKey" : "Y4555c7P4LM6Y555XW2PKx4555BMFxO555g/sJ555KU=",
        "blocked" : false,
        "inboxPosition" : null,
        "archived" : false
    },
    "+31638422999" : {
        "name" : null,
        "uuid" : "1999f999-eb02-999d-a1b6-999517909992",
        "color" : null,
        "messageExpirationTime" : 0,
        "profileKey" : "Y4999c7P4LM6Y999XW2PKx4999BMFxO999g/sJ999KU=",
        "blocked" : false,
        "inboxPosition" : null,
        "archived" : false
    }
}
```

### Response to 'getGroups'

```
{
    "DK555555UjPU55545557bA==" : {
        "name" : "Watchdogs inc.",
        "color" : null,
        "messageExpirationTime" : 0,
        "blocked" : false,
        "inboxPosition" : null,
        "archived" : false,
        "avatarId" : 0,
        "members" : [ "+31638555555", "+31638999999" ]
    },
    "Y5555rtl2p/TnLYvY555dA==" : {
        "name" : "Freddy ftw",
        "color" : null,
        "messageExpirationTime" : 0,
        "blocked" : false,
        "inboxPosition" : null,
        "archived" : false,
        "avatarId" : 0,
        "members" : [ "+31638555555", "+31638999999" ]
    }
}
```
