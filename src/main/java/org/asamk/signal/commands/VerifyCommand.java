package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.Manager;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;

public class VerifyCommand implements LocalCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("verificationCode")
                .help("The verification code you received via sms or voice call.");
        subparser.addArgument("-p", "--pin")
                .help("The registration lock PIN, that was set by the user (Optional)");
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        if (m.isRegistered()) {
            System.err.println("User registration is already verified");
            return 1;
        }
        try {
            String verificationCode = ns.getString("verificationCode");
            String pin = ns.getString("pin");
            m.verifyAccount(verificationCode, pin);
            return 0;
        } catch (LockedException e) {
            System.err.println("Verification failed! This number is locked with a pin. Hours remaining until reset: " + (e.getTimeRemaining() / 1000 / 60 / 60));
            System.err.println("Use '--pin PIN_CODE' to specify the registration lock PIN");
            return 3;
        } catch (IOException e) {
            System.err.println("Verify error: " + e.getMessage());
            return 3;
        }
    }
}
