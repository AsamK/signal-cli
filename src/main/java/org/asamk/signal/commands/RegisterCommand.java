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
        final boolean voiceVerification = ns.getBoolean("voice");
        final var captcha = ns.getString("captcha");

        try {
            m.register(voiceVerification, captcha);
            return 0;
        } catch (CaptchaRequiredException e) {
            if (captcha == null) {
                System.err.println("Captcha required for verification, use --captcha CAPTCHA");
                System.err.println("To get the token, go to https://signalcaptchas.org/registration/generate.html");
                System.err.println("Check the developer tools (F12) console for a failed redirect to signalcaptcha://");
                System.err.println("Everything after signalcaptcha:// is the captcha token.");
            } else {
                System.err.println("Invalid captcha given.");
            }
            return 1;
        } catch (IOException e) {
            System.err.println("Request verify error: " + e.getMessage());
            return 3;
        }
    }
}
