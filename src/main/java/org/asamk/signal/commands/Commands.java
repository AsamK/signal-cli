package org.asamk.signal.commands;

import java.util.HashMap;
import java.util.Map;

public class Commands {

    private static final Map<String, Command> commands = new HashMap<>();

    static {
        addCommand("addDevice", new AddDeviceCommand());
        addCommand("block", new BlockCommand());
        addCommand("daemon", new DaemonCommand());
        addCommand("stdio", new StdioCommand());
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
        addCommand("removePin", new RemovePinCommand());
        addCommand("send", new SendCommand());
        addCommand("sendReaction", new SendReactionCommand());
        addCommand("sendContacts", new SendContactsCommand());
        addCommand("updateContact", new UpdateContactCommand());
        addCommand("setPin", new SetPinCommand());
        addCommand("trust", new TrustCommand());
        addCommand("unblock", new UnblockCommand());
        addCommand("unregister", new UnregisterCommand());
        addCommand("updateAccount", new UpdateAccountCommand());
        addCommand("updateGroup", new UpdateGroupCommand());
        addCommand("updateProfile", new UpdateProfileCommand());
        addCommand("verify", new VerifyCommand());
        addCommand("uploadStickerPack", new UploadStickerPackCommand());
    }

    public static Map<String, Command> getCommands() {
        return commands;
    }

    private static void addCommand(String name, Command command) {
        commands.put(name, command);
    }
}
