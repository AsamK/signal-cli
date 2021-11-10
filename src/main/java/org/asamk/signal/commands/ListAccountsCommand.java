package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.exceptions.CommandException;

import java.util.stream.Collectors;

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
            final Namespace ns, final SignalCreator c, final OutputWriter outputWriter
    ) throws CommandException {
        final var accountNumbers = c.getAccountNumbers();
        if (outputWriter instanceof JsonWriter jsonWriter) {
            final var jsonAccounts = accountNumbers.stream().map(JsonAccount::new).collect(Collectors.toList());
            jsonWriter.write(jsonAccounts);
        } else if (outputWriter instanceof PlainTextWriter plainTextWriter) {
            for (final var number : accountNumbers) {
                plainTextWriter.println("Number: {}", number);
            }
        }
    }

    private record JsonAccount(String number) {}
}
