package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.RegistrationManager;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;

public class VerifyCommand implements RegistrationCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("verificationCode").help("The verification code you received via sms or voice call.");
        subparser.addArgument("-p", "--pin").help("The registration lock PIN, that was set by the user (Optional)");
    }

    @Override
    public void handleCommand(final Namespace ns, final RegistrationManager m) throws CommandException {
        var verificationCode = ns.getString("verificationCode");
        var pin = ns.getString("pin");

        try {
            final var manager = m.verifyAccount(verificationCode, pin);
            manager.close();
        } catch (LockedException e) {
            throw new UserErrorException(
                    "Verification failed! This number is locked with a pin. Hours remaining until reset: "
                            + (e.getTimeRemaining() / 1000 / 60 / 60)
                            + "\nUse '--pin PIN_CODE' to specify the registration lock PIN");
        } catch (KeyBackupServicePinException e) {
            throw new UserErrorException("Verification failed! Invalid pin, tries remaining: " + e.getTriesRemaining());
        } catch (KeyBackupSystemNoDataException e) {
            throw new UnexpectedErrorException("Verification failed! No KBS data.");
        } catch (IOException e) {
            throw new IOErrorException("Verify error: " + e.getMessage());
        }
    }
}
