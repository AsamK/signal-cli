package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
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
    public void handleCommand(final Namespace ns, final RegistrationManager m) throws CommandException {
        final boolean voiceVerification = ns.getBoolean("voice");
        final var captcha = ns.getString("captcha");

        try {
            m.register(voiceVerification, captcha);
        } catch (CaptchaRequiredException e) {
            String message;
            if (captcha == null) {
                message = "Captcha required for verification, use --captcha CAPTCHA\n"
                        + "To get the token, go to https://signalcaptchas.org/registration/generate.html\n"
                        + "Check the developer tools (F12) console for a failed redirect to signalcaptcha://\n"
                        + "Everything after signalcaptcha:// is the captcha token.";
            } else {
                message = "Invalid captcha given.";
            }
            throw new UserErrorException(message);
        } catch (IOException e) {
            throw new IOErrorException("Request verify error: " + e.getMessage());
        }
    }
}
