package org.asamk.signal.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Commands {

    private static final Map<String, Command> commands = new HashMap<>();
    private static final Map<String, SubparserAttacher> commandSubparserAttacher = new TreeMap<>();

    static {
        addCommand(new AddDeviceCommand());
        addCommand(new BlockCommand());
        addCommand(new DaemonCommand());
        addCommand(new DeleteLocalAccountDataCommand());
        addCommand(new FinishLinkCommand());
        addCommand(new GetAttachmentCommand());
        addCommand(new GetUserStatusCommand());
        addCommand(new JoinGroupCommand());
        addCommand(new JsonRpcDispatcherCommand());
        addCommand(new LinkCommand());
        addCommand(new ListAccountsCommand());
        addCommand(new ListContactsCommand());
        addCommand(new ListDevicesCommand());
        addCommand(new ListGroupsCommand());
        addCommand(new ListIdentitiesCommand());
        addCommand(new ListStickerPacksCommand());
        addCommand(new QuitGroupCommand());
        addCommand(new ReceiveCommand());
        addCommand(new RegisterCommand());
        addCommand(new RemoveContactCommand());
        addCommand(new RemoveDeviceCommand());
        addCommand(new RemovePinCommand());
        addCommand(new RemoteDeleteCommand());
        addCommand(new SendCommand());
        addCommand(new SendContactsCommand());
        addCommand(new SendPaymentNotificationCommand());
        addCommand(new SendReactionCommand());
        addCommand(new SendReceiptCommand());
        addCommand(new SendSyncRequestCommand());
        addCommand(new SendTypingCommand());
        addCommand(new SetPinCommand());
        addCommand(new SubmitRateLimitChallengeCommand());
        addCommand(new StartLinkCommand());
        addCommand(new TrustCommand());
        addCommand(new UnblockCommand());
        addCommand(new UnregisterCommand());
        addCommand(new UpdateAccountCommand());
        addCommand(new UpdateConfigurationCommand());
        addCommand(new UpdateContactCommand());
        addCommand(new UpdateGroupCommand());
        addCommand(new UpdateProfileCommand());
        addCommand(new UploadStickerPackCommand());
        addCommand(new VerifyCommand());
        addCommand(new VersionCommand());
    }

    public static Map<String, SubparserAttacher> getCommandSubparserAttachers() {
        return commandSubparserAttacher;
    }

    public static Command getCommand(String commandKey) {
        if (!commands.containsKey(commandKey)) {
            return null;
        }
        return commands.get(commandKey);
    }

    private static void addCommand(Command command) {
        commands.put(command.getName(), command);
        if (command instanceof CliCommand) {
            commandSubparserAttacher.put(command.getName(), ((CliCommand) command)::attachToSubparser);
        }
    }
}
