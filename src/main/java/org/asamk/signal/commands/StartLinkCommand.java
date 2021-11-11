package org.asamk.signal.commands;

import org.asamk.signal.JsonWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.MultiAccountManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public class StartLinkCommand implements JsonRpcMultiCommand<Void> {

    private final static Logger logger = LoggerFactory.getLogger(StartLinkCommand.class);

    @Override
    public String getName() {
        return "startLink";
    }

    @Override
    public void handleCommand(
            final Void request, final MultiAccountManager m, final JsonWriter jsonWriter
    ) throws CommandException {
        final URI deviceLinkUri;
        try {
            deviceLinkUri = m.getNewProvisioningDeviceLinkUri();
        } catch (TimeoutException e) {
            throw new UserErrorException("Device link creation timed out, please try again.");
        } catch (IOException e) {
            throw new IOErrorException("Link request error: " + e.getMessage(), e);
        }

        jsonWriter.write(new JsonLink(deviceLinkUri.toString()));
    }

    private record JsonLink(String deviceLinkUri) {}
}
