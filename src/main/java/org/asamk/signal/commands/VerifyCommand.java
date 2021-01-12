package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

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
    public int handleCommand(final Namespace ns, final RegistrationManager m) {
        try {
            String verificationCode = ns.getString("verificationCode");
            String pin = ns.getString("pin");
            m.verifyAccount(verificationCode, pin);
            return 0;
        } catch (LockedException e) {
            System.err.println("Verification failed! This number is locked with a pin. Hours remaining until reset: "
                    + (e.getTimeRemaining() / 1000 / 60 / 60));
            System.err.println("Use '--pin PIN_CODE' to specify the registration lock PIN");
            return 1;
        } catch (KeyBackupServicePinException e) {
            System.err.println("Verification failed! Invalid pin, tries remaining: " + e.getTriesRemaining());
            return 1;
        } catch (KeyBackupSystemNoDataException e) {
            System.err.println("Verification failed! No KBS data.");
            return 3;
        } catch (IOException e) {
            System.err.println("Verify error: " + e.getMessage());
            return 3;
        }
    }
}
