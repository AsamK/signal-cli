package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.manager.RegistrationManager;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;

import java.io.IOException;

public class RegisterCommand implements RegistrationCommand {

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not sms.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--captcha")
                .help("The captcha token, required if registration failed with a captcha required error.");
    }

    @Override
    public int handleCommand(final Namespace ns, final RegistrationManager m) {
        try {
            final boolean voiceVerification = ns.getBoolean("voice");
            final String captcha = ns.getString("captcha");
            m.register(voiceVerification, captcha);
            return 0;
        } catch (CaptchaRequiredException e) {
            System.err.println("Captcha invalid or required for verification (" + e.getMessage() + ")");
            return 1;
        } catch (IOException e) {
            System.err.println("Request verify error: " + e.getMessage());
            return 3;
        }
    }
}
