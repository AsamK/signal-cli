package org.asamk.signal.commands;

import java.util.HashMap;
import java.util.Map;

public class Commands {

    private static final Map<String, Command> commands = new HashMap<>();

    static {
        addCommand("addDevice", new AddDeviceCommand());
        addCommand("block", new BlockCommand());
        addCommand("daemon", new DaemonCommand());
        addCommand("getUserStatus", new GetUserStatusCommand());
        addCommand("link", new LinkCommand());
        addCommand("listContacts", new ListContactsCommand());
        addCommand("listDevices", new ListDevicesCommand());
        addCommand("listGroups", new ListGroupsCommand());
        addCommand("listIdentities", new ListIdentitiesCommand());
        addCommand("joinGroup", new JoinGroupCommand());
        addCommand("quitGroup", new QuitGroupCommand());
        addCommand("receive", new ReceiveCommand());
        addCommand("register", new RegisterCommand());
        addCommand("removeDevice", new RemoveDeviceCommand());
        addCommand("remoteDelete", new RemoteDeleteCommand());
        addCommand("removePin", new RemovePinCommand());
        addCommand("send", new SendCommand());
        addCommand("sendContacts", new SendContactsCommand());
        addCommand("sendReaction", new SendReactionCommand());
        addCommand("sendSyncRequest", new SendSyncRequestCommand());
        addCommand("setPin", new SetPinCommand());
        addCommand("trust", new TrustCommand());
        addCommand("unblock", new UnblockCommand());
        addCommand("unregister", new UnregisterCommand());
        addCommand("updateAccount", new UpdateAccountCommand());
        addCommand("updateContact", new UpdateContactCommand());
        addCommand("updateGroup", new UpdateGroupCommand());
        addCommand("updateProfile", new UpdateProfileCommand());
        addCommand("uploadStickerPack", new UploadStickerPackCommand());
        addCommand("verify", new VerifyCommand());
    }

    public static Map<String, Command> getCommands() {
        return commands;
    }

    public static Command getCommand(String commandKey) {
        if (!commands.containsKey(commandKey)) {
            return null;
        }
        return commands.get(commandKey);
    }

    private static void addCommand(String name, Command command) {
        commands.put(name, command);
    }
}
