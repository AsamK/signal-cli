package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.api.IncorrectPinException;
import org.asamk.signal.manager.api.PinLockedException;
import org.asamk.signal.output.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class VerifyCommand implements RegistrationCommand, JsonRpcRegistrationCommand<VerifyCommand.VerifyParams> {

    private final static Logger logger = LoggerFactory.getLogger(VerifyCommand.class);

    @Override
    public String getName() {
        return "verify";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Verify the number using the code received via SMS or voice.");
        subparser.addArgument("verification-code").help("The verification code you received via sms or voice call.");
        subparser.addArgument("-p", "--pin").help("The registration lock PIN, that was set by the user (Optional)");
    }

    @Override
    public void handleCommand(final Namespace ns, final RegistrationManager m) throws CommandException {
        var verificationCode = ns.getString("verification-code");
        var pin = ns.getString("pin");

        verify(m, verificationCode, pin);
    }

    @Override
    public TypeReference<VerifyParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final VerifyParams request, final RegistrationManager m, final JsonWriter jsonWriter
    ) throws CommandException {
        verify(m, request.verificationCode(), request.pin());
    }

    private void verify(
            final RegistrationManager m, final String verificationCode, final String pin
    ) throws UserErrorException, IOErrorException {
        try {
            m.verifyAccount(verificationCode, pin);
        } catch (PinLockedException e) {
            throw new UserErrorException(
                    "Verification failed! This number is locked with a pin. Hours remaining until reset: "
                            + (e.getTimeRemaining() / 1000 / 60 / 60)
                            + "\nUse '--pin PIN_CODE' to specify the registration lock PIN");
        } catch (IncorrectPinException e) {
            throw new UserErrorException("Verification failed! Invalid pin, tries remaining: " + e.getTriesRemaining());
        } catch (IOException e) {
            throw new IOErrorException("Verify error: " + e.getMessage(), e);
        }
    }

    public record VerifyParams(String verificationCode, String pin) {}
}
