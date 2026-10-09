package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

public class StartLinkCommand implements JsonRpcMultiCommand<StartLinkCommand.StartLinkParams> {

    private static final Logger logger = LoggerFactory.getLogger(StartLinkCommand.class);

    @Override
    public String getName() {
        return "startLink";
    }

    @Override
    public TypeReference<StartLinkParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public void handleCommand(
            final StartLinkParams request,
            final MultiAccountManager m,
            final JsonWriter jsonWriter
    ) throws CommandException {
        final URI deviceLinkUri;
        try {
            deviceLinkUri = m.getNewProvisioningDeviceLinkUri(request != null && Boolean.TRUE.equals(request.importHistory()));
        } catch (TimeoutException e) {
            throw new UserErrorException("Device link creation timed out, please try again.");
        } catch (IOException e) {
            throw new IOErrorException("Link request error: " + e.getMessage(), e);
        }

        jsonWriter.write(new JsonLink(deviceLinkUri.toString()));
    }

    public record StartLinkParams(Boolean importHistory) {}

    private record JsonLink(String deviceLinkUri) {}
}
