package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.manager.api.UserAlreadyExistsException;
import org.asamk.signal.output.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

public class FinishLinkCommand implements JsonRpcMultiCommand<FinishLinkCommand.FinishLinkParams> {

    private final static Logger logger = LoggerFactory.getLogger(FinishLinkCommand.class);

    @Override
    public String getName() {
        return "finishLink";
    }

    @Override
    public TypeReference<FinishLinkParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public void handleCommand(
            final FinishLinkParams request, final MultiAccountManager m, final JsonWriter jsonWriter
    ) throws CommandException {
        if (request.deviceLinkUri() == null) {
            throw new UserErrorException("Missing deviceLinkUri.");
        }
        final URI deviceLinkUri;
        try {
            deviceLinkUri = new URI(request.deviceLinkUri());
        } catch (URISyntaxException e) {
            throw new UserErrorException("Invalid device link uri.");
        }
        final var provisioningManager = m.getProvisioningManagerFor(deviceLinkUri);
        if (provisioningManager == null) {
            throw new UserErrorException("Unknown device link uri.");
        }

        var deviceName = request.deviceName();
        if (deviceName == null) {
            deviceName = "cli";
        }
        final String number;
        try {
            number = provisioningManager.finishDeviceLink(deviceName);
        } catch (TimeoutException e) {
            throw new UserErrorException("Link request timed out, please try again.");
        } catch (IOException e) {
            throw new IOErrorException("Link request error: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")", e);
        } catch (UserAlreadyExistsException e) {
            throw new UserErrorException("The user "
                    + e.getNumber()
                    + " already exists\nDelete \""
                    + e.getFileName()
                    + "\" before trying again.");
        }

        jsonWriter.write(new JsonFinishLink(number));
    }

    public record FinishLinkParams(String deviceLinkUri, String deviceName) {}

    private record JsonFinishLink(String number) {}
}
