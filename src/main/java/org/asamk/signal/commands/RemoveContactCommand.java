package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

public class RemoveContactCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "removeContact";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Remove the details of a given contact");
        subparser.addArgument("recipient").help("Contact number");
        final var mut = subparser.addMutuallyExclusiveGroup();
        mut.addArgument("--hide")
                .action(Arguments.storeTrue())
                .help("Hide the contact in the contact list, but keep the data.");
        mut.addArgument("--forget")
                .action(Arguments.storeTrue())
                .help("Delete all data associated with this contact, including identity keys and sessions.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        var recipientString = ns.getString("recipient");
        var recipient = CommandUtil.getSingleRecipientIdentifier(recipientString, m.getSelfNumber());

        var hide = Boolean.TRUE == ns.getBoolean("hide");
        var forget = Boolean.TRUE == ns.getBoolean("forget");
        if (hide) {
            m.hideRecipient(recipient);
        } else if (forget) {
            m.deleteRecipient(recipient);
        } else {
            m.deleteContact(recipient);
        }
    }
}
