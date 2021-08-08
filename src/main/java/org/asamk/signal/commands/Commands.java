package org.asamk.signal.commands;

import org.asamk.signal.OutputWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Commands {

    private static final Map<String, CommandConstructor> commands = new HashMap<>();
    private static final Map<String, SubparserAttacher> commandSubparserAttacher = new TreeMap<>();

    static {
        addCommand("addDevice", AddDeviceCommand::new, AddDeviceCommand::attachToSubparser);
        addCommand("block", BlockCommand::new, BlockCommand::attachToSubparser);
        addCommand("daemon", DaemonCommand::new, DaemonCommand::attachToSubparser);
        addCommand("getUserStatus", GetUserStatusCommand::new, GetUserStatusCommand::attachToSubparser);
        addCommand("link", LinkCommand::new, LinkCommand::attachToSubparser);
        addCommand("listContacts", ListContactsCommand::new, ListContactsCommand::attachToSubparser);
        addCommand("listDevices", ListDevicesCommand::new, ListDevicesCommand::attachToSubparser);
        addCommand("listGroups", ListGroupsCommand::new, ListGroupsCommand::attachToSubparser);
        addCommand("listIdentities", ListIdentitiesCommand::new, ListIdentitiesCommand::attachToSubparser);
        addCommand("joinGroup", JoinGroupCommand::new, JoinGroupCommand::attachToSubparser);
        addCommand("quitGroup", QuitGroupCommand::new, QuitGroupCommand::attachToSubparser);
        addCommand("receive", ReceiveCommand::new, ReceiveCommand::attachToSubparser);
        addCommand("register", RegisterCommand::new, RegisterCommand::attachToSubparser);
        addCommand("removeDevice", RemoveDeviceCommand::new, RemoveDeviceCommand::attachToSubparser);
        addCommand("remoteDelete", RemoteDeleteCommand::new, RemoteDeleteCommand::attachToSubparser);
        addCommand("removePin", RemovePinCommand::new, RemovePinCommand::attachToSubparser);
        addCommand("send", SendCommand::new, SendCommand::attachToSubparser);
        addCommand("sendContacts", SendContactsCommand::new, SendContactsCommand::attachToSubparser);
        addCommand("sendReaction", SendReactionCommand::new, SendReactionCommand::attachToSubparser);
        addCommand("sendSyncRequest", SendSyncRequestCommand::new, SendSyncRequestCommand::attachToSubparser);
        addCommand("sendTyping", SendTypingCommand::new, SendTypingCommand::attachToSubparser);
        addCommand("setPin", SetPinCommand::new, SetPinCommand::attachToSubparser);
        addCommand("trust", TrustCommand::new, TrustCommand::attachToSubparser);
        addCommand("unblock", UnblockCommand::new, UnblockCommand::attachToSubparser);
        addCommand("unregister", UnregisterCommand::new, UnregisterCommand::attachToSubparser);
        addCommand("updateAccount", UpdateAccountCommand::new, UpdateAccountCommand::attachToSubparser);
        addCommand("updateContact", UpdateContactCommand::new, UpdateContactCommand::attachToSubparser);
        addCommand("updateGroup", UpdateGroupCommand::new, UpdateGroupCommand::attachToSubparser);
        addCommand("updateProfile", UpdateProfileCommand::new, UpdateProfileCommand::attachToSubparser);
        addCommand("uploadStickerPack", UploadStickerPackCommand::new, UploadStickerPackCommand::attachToSubparser);
        addCommand("verify", VerifyCommand::new, VerifyCommand::attachToSubparser);
    }

    public static Map<String, SubparserAttacher> getCommandSubparserAttachers() {
        return commandSubparserAttacher;
    }

    public static Command getCommand(String commandKey, OutputWriter outputWriter) {
        if (!commands.containsKey(commandKey)) {
            return null;
        }
        return commands.get(commandKey).constructCommand(outputWriter);
    }

    private static void addCommand(
            String name, CommandConstructor commandConstructor, SubparserAttacher subparserAttacher
    ) {
        commands.put(name, commandConstructor);
        commandSubparserAttacher.put(name, subparserAttacher);
    }

    private interface CommandConstructor {

        Command constructCommand(OutputWriter outputWriter);
    }
}
