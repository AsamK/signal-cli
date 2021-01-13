# TCP daemon mode

signal-cli can run in daemon mode and listen to incoming TCP connections.
Once a client connected, the daemon is accepting commands wrapped in JSON objects.
Each requested command results in a corresponding response.

Multiple commands can be send using the same TCP connection. Invalid commands -
e.g. invalid JSON syntax - will result in error responses, but the connection
will not be terminated.

## send message

Sending a message to one recipient or one group. Attachments can be attached as
as base64 encoded string.
`recipient` and `groupId` must not be provided at the same time.

### Request `send_message`
```JSON
{
    "command": "send_message",
    "reqId": "[request ID (optional)]",

    "recipient": "[phone number]",
    "groupId": "[base64 group ID]",

    "dataMessage": {
        "message": "[text message (optional)]",
        "attachments": [{
            "base64Data": "[base64 encoded data]",
            "filename": "[filename (optional)]"
        }]
    }
}
```

### Response `send_message`
```JSON
{
    "command": "send_message",
    "reqId": "[referencing request ID]",

    "statusCode": 0,
    "timestamp": 1234567
}
```

## send reaction

Reacting to an existing message.
`recipient` and `groupId` must not be provided at the same time.

### Request `send_reaction`
```JSON
{
    "command": "send_reaction",
    "reqId": "[request ID (optional)]",

    "recipient": "[phone number]",
    "groupId": "[base64 group ID]",

    "reaction": {
        "emoji": "😀",
        "author": "[phone number of original message]",
        "remove": false,
        "timestamp": 1234567
    }
}
```

### Response `send_reaction`
```JSON
{
    "command": "send_reaction",
    "reqId": "[referencing request ID]",

    "statusCode": 0,
    "timestamp": 1234567
}
```

## receive messages

Receiving incoming messages. Command waits for `timeout` milliseconds for new messages. Default `1000`.

Attachments can be omitted in the command response with `ignoreAttachments`. Default `false`.

### Request `receive_messages`
```JSON
{
    "command": "receive_messages",
    "reqId": "[request ID (optional)]",

    "timeout": 1000,
    "ignoreAttachments": false
}
```

### Response `receive_messages`
```JSON
{
    "command": "receive_messages",
    "reqId": "[referencing request ID]",

    "statusCode": 0,
    "messages": [{
        "timestamp": 1234567,
        "sender": "[senders phone number]",
        "body": "[text message]",
        "attachments": [{
            "base64Data": "[base64 encoded data]",
            "filename": "[filename (optional)]"
        }]
    },{
        "statusCode": -1,
        "errorMessage": "[message parsing error]"
    }]
}
```

## Error response
```JSON
{
    "command": "[requested command]",
    "reqId": "[referencing request ID]",

    "statusCode": -1,
    "errorMessage": "[additional information]"
}
```

# Example
Running the client
```
signal-cli --username +436XXYYYZZZZ socket
```

Sending command with e.g. `socat`
```
echo "{command:'receive_messages'}" | socat -t 2 TCP:localhost:6789
```


# Status codes

| Code | Value                   | Description                                                 |
| ----:| ----------------------- | ----------------------------------------------------------- |
|   -1 | UNKNOWN                 | Unknown error. see `errorMessage`                           |
|    0 | SUCCESS                 | Command successfully executed                               |
|    1 | UNKNOWN_COMMAND         | Unknown or not implemented command                          |
|    2 | INVALID_NUMBER          | Invalid `recipient` number                                  |
|    3 | INVALID_ATTACHMENT      | Error while parsing attachment                              |
|    4 | INVALID_JSON            | Invalid JSON received                                       |
|    5 | INVALID_RECIPIENT       | None or both of `recipient` and `groupId` are set           |
|    6 | GROUP_NOT_FOUND         | Invalid `groupId` provided                                  |
|    7 | NOT_A_GROUP_MEMBER      | User isn't a member of provided `groupId`                   |
|    8 | MESSAGE_ERROR           | Received error while fetching messages                      |
|    9 | MISSING_MESSAGE_CONTENT | Missing message content. Can't parse message                |
|   10 | MESSAGE_PARSING_ERROR   | General error while parsing the message. see `errorMessage` |
|   11 | MISSING_REACTION        | Incomplete reaction request                                 |
