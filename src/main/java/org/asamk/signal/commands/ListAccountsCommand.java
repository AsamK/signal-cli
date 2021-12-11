package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;

public class ListAccountsCommand implements JsonRpcMultiLocalCommand {

    @Override
    public String getName() {
        return "listAccounts";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of registered accounts.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final MultiAccountManager c, final OutputWriter outputWriter
    ) throws CommandException {
        final var accountNumbers = c.getAccountNumbers();
        if (outputWriter instanceof JsonWriter jsonWriter) {
            final var jsonAccounts = accountNumbers.stream().map(JsonAccount::new).toList();
            jsonWriter.write(jsonAccounts);
        } else if (outputWriter instanceof PlainTextWriter plainTextWriter) {
            for (final var number : accountNumbers) {
                plainTextWriter.println("Number: {}", number);
            }
        }
    }

    private record JsonAccount(String number) {}
}
