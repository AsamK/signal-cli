package org.asamk.signal.socket;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.io.IOException;
import java.net.Socket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.storage.SignalAccount;
import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.util.Util;
import org.asamk.signal.util.GroupIdFormatException;
import org.asamk.signal.manager.GroupNotFoundException;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.NotAGroupMemberException;
import org.whispersystems.util.Base64;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import static org.asamk.signal.util.ErrorUtils.handleGroupIdFormatException;
import org.asamk.signal.socket.SocketTasks;

public class Commander {
    private Manager m;
    private SocketTasks tasks;
    private ObjectMapper mapper;

    public Commander(Manager manager, SocketTasks socketTasks) {
        this.m = manager;
        this.tasks = socketTasks;
        this.mapper = new ObjectMapper();
    }

    public void handleCommand(String command, JsonNode arguments) {
        switch (command) {
            case "getContacts":
                this.tasks.send(this.getContacts());
                break;
            case "updateContacts":
                this.updateContacts(arguments);
                break;
            case "sendMessage":
                this.sendMessage(arguments);
                break;
            case "trust":
                this.trust(arguments);
                break;
            case "endSession":
                this.endSession(arguments);
                break;
            case "getGroups":
                this.tasks.send(this.getGroups());
                break;
            default:
                this.tasks.send("Unknown command: '" + command + "'");
        }
    }

    private String getGroups() {
        ObjectNode node = this.mapper.createObjectNode();
        List<GroupInfo> groups = this.m.getAccount().getGroupStore().getGroups();
        for (GroupInfo g: groups) {
            ObjectNode group = this.mapper.createObjectNode();
            group.put("name", g.name);
            group.put("color", g.color);
            group.put("messageExpirationTime", g.messageExpirationTime);
            group.put("blocked", g.blocked);
            group.put("inboxPosition", g.inboxPosition);
            group.put("archived", g.archived);
            group.put("avatarId", g.getAvatarId());
            for (String m: g.getMembersE164()) {
                group.putArray("members").add(m);
            }
            node.set(Base64.encodeBytes(g.groupId), group);
        }
        String out = null;
        try {
            out = this.mapper.writeValueAsString(node); //writerWithDefaultPrettyPrinter()
        } catch (Exception e) {
            System.err.println("Could not send groups");
        }
        return out;
    }

    private void sendMessage(JsonNode args) {
        Iterator<Map.Entry<String,JsonNode>> entryIt = args.fields();
        Map.Entry<String,JsonNode> entry;
        String argument;
        String message = null;
        ArrayList<String> contacts = null;
        ArrayList<String> groups = null;
        while (entryIt.hasNext()) {
            entry = entryIt.next();
            argument = entry.getKey();
            switch(argument) {
             case "contacts":
                try {
                    contacts = this.mapper.readValue(entry.getValue().toString(), new TypeReference<ArrayList<String>>(){});
                } catch (IOException e) {
                    System.err.println("bad list of contacts");
                    return;
                }
                break;
             case "groups":
                try {
                    groups = this.mapper.readValue(entry.getValue().toString(), new TypeReference<ArrayList<String>>(){});
                } catch (IOException e) {
                    System.err.println("bad list of groups");
                    return;
                }
                break;
            case "message":
                message = entry.getValue().asText();
                if (message.trim().length() <= 0) {
                    System.err.println("cannot send empty message");
                    return;
                }
                break;
            }
        }
        if (contacts != null) {
            try {
                this.m.sendMessage(message, new ArrayList<>(), contacts);
            } catch (Exception | EncapsulatedExceptions | InvalidNumberException e) {
                System.err.println(e);
            }
        }
        if (groups != null) {
            byte[] groupId;
            for (String group: groups) {
                try {
                    groupId = Util.decodeGroupId(group);
                } catch (GroupIdFormatException e) {
                    handleGroupIdFormatException(e);
                    continue;
                }
                try {
                    this.m.sendGroupMessage(message, new ArrayList<>(), groupId);
                } catch (IOException | EncapsulatedExceptions | GroupNotFoundException | AttachmentInvalidException | NotAGroupMemberException e) {
                    System.err.println(e);
                }
            }
        }
    }

    private void endSession(JsonNode args) {
        Iterator<Map.Entry<String,JsonNode>> entryIt = args.fields();
        Map.Entry<String,JsonNode> entry;
        String argument;
        ArrayList<String> contacts = null;
        while (entryIt.hasNext()) {
            entry = entryIt.next();
            argument = entry.getKey();
            switch(argument) {
             case "contacts":
                try {
                    contacts = this.mapper.readValue(entry.getValue().toString(), new TypeReference<ArrayList<String>>(){});
                } catch (IOException e) {
                    System.err.println("bad list of contacts");
                    return;
                }
                break;
            }
        }
        if (contacts != null) {
            try {
                this.m.sendEndSessionMessage(contacts);
                System.err.println("WARNING: Ended session with " + String.join(", ", contacts));
            } catch (Exception | EncapsulatedExceptions | InvalidNumberException e) {
                System.err.println(e);
            }
        }
    }

    private void trust(JsonNode args) {
        Iterator<Map.Entry<String,JsonNode>> entryIt = args.fields();
        Map.Entry<String,JsonNode> entry;
        String argument;
        ArrayList<String> contacts = null;
        while (entryIt.hasNext()) {
            entry = entryIt.next();
            argument = entry.getKey();
            switch(argument) {
             case "contacts":
                try {
                    contacts = this.mapper.readValue(entry.getValue().toString(), new TypeReference<ArrayList<String>>(){});
                } catch (IOException e) {
                    System.err.println("bad list of contacts");
                    return;
                }
                break;
            }
        }
        if (contacts != null) {
            for (String contact: contacts) {
                this.m.trustIdentityAllKeys(contact);
            }
            System.err.println("WARNING: Updated trust for all keys from " + String.join(", ", contacts));
        }
    }

    private String getContacts() {
        ObjectNode node = this.mapper.createObjectNode();
        List<ContactInfo> contacts = this.m.getContacts();
        for (ContactInfo c: contacts) {
            ObjectNode group = this.mapper.createObjectNode();
            group.put("name", c.name);
            group.put("uuid", c.uuid.toString());
            group.put("color", c.color);
            group.put("messageExpirationTime", c.messageExpirationTime);
            group.put("profileKey", c.profileKey);
            group.put("blocked", c.blocked);
            group.put("inboxPosition", c.inboxPosition);
            group.put("archived", c.archived);
            node.set(c.number, group);
        }
        String out = null;
        try {
            out = this.mapper.writeValueAsString(node); //writerWithDefaultPrettyPrinter()
        } catch (Exception e) {
            System.err.println("Could not send contacts");
        }
        return out;
    }

    private void updateContacts(JsonNode args) {
        Iterator<Map.Entry<String,JsonNode>> entryIt = args.fields();
        Map.Entry<String,JsonNode> entry;
        String number;
        while (entryIt.hasNext()) {
            entry = entryIt.next();
            number = entry.getKey();
            ContactInfo contactInfo = this.m.getContact(number);
            if (contactInfo == null) {
                System.err.println("Could not update " + number);
                continue;
            } else {
                patchContactInfo(contactInfo, entry.getValue());
                SignalAccount account = this.m.getAccount();
                account.getContactStore().updateContact(contactInfo);
                account.save();
                System.out.println("Updated account info for " + number);
            }
        }
    }

    private void patchContactInfo(ContactInfo contactInfo, JsonNode args) {
        Iterator<Map.Entry<String,JsonNode>> entryIt = args.fields();
        Map.Entry<String,JsonNode> entry;
        String argument;
        JsonNode value;
        while (entryIt.hasNext()) {
            entry = entryIt.next();
            argument = entry.getKey();
            value = entry.getValue();
            switch (argument) {
                case "name":
                    contactInfo.name = value.asText();
                    break;
                case "color":
                    contactInfo.color = value.asText();
                    break;
                case "messageExpirationTime":
                    contactInfo.messageExpirationTime = value.asInt();
                    break;
                case "blocked":
                    contactInfo.blocked = value.asBoolean();
                    break;
                case "inboxPosition":
                    contactInfo.inboxPosition = value.asInt();
                    break;
                case "archived":
                    contactInfo.archived = value.asBoolean();
                    break;
                default:
                    System.err.println("Invalid field '" + argument + "' in " + contactInfo.number);
            }
        }
    }

}
