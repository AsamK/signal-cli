package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.output.JsonWriter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DeleteLocalAccountDataCommand implements RegistrationCommand, JsonRpcRegistrationCommand<Map<String, Object>> {

    @Override
    public String getName() {
        return "deleteLocalAccountData";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help(
                "Delete all local data for this account. Data should only be deleted if the account is unregistered. CAUTION: This cannot be undone.");
        subparser.addArgument("--ignore-registered")
                .help("Delete the account data even though the account is still registered on the Signal servers.")
                .action(Arguments.storeTrue());
    }

    @Override
    public void handleCommand(final Namespace ns, final RegistrationManager m) throws CommandException {
        try {
            final var ignoreRegistered = Boolean.TRUE.equals(ns.getBoolean("ignore-registered"));
            if (m.isRegistered() && !ignoreRegistered) {
                throw new UserErrorException(
                        "Not deleting account, it is still registered. Use --ignore-registered to delete it anyway.");
            }

            m.deleteLocalAccountData();
        } catch (IOException e) {
            throw new IOErrorException("Deletion error: " + e.getMessage(), e);
        }
    }

    @Override
    public TypeReference<Map<String, Object>> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            Map<String, Object> request, RegistrationManager m, JsonWriter jsonWriter
    ) throws CommandException {
        Namespace commandNamespace = new JsonRpcNamespace(request == null ? Map.of() : request);
        handleCommand(commandNamespace, m);
    }
}
