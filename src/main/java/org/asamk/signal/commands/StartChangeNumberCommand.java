package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.RateLimitErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.NotPrimaryDeviceException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;

public class StartChangeNumberCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "startChangeNumber";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Change account to a new phone number with SMS or voice verification.");
        subparser.addArgument("number").help("The new phone number in E164 format.").required(true);
        subparser.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not SMS.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--captcha")
                .help("The captcha token, required if change number failed with a captcha required error.");
    }

    @Override
    public void handleCommand(
            final Namespace ns, final Manager m, final OutputWriter outputWriter
    ) throws CommandException {
        final var newNumber = ns.getString("number");
        final var voiceVerification = Boolean.TRUE.equals(ns.getBoolean("voice"));
        final var captcha = ns.getString("captcha");

        try {
            m.startChangeNumber(newNumber, voiceVerification, captcha);
        } catch (RateLimitException e) {
            final var message = CommandUtil.getRateLimitMessage(e);
            throw new RateLimitErrorException(message, e);
        } catch (CaptchaRequiredException e) {
            final var message = CommandUtil.getCaptchaRequiredMessage(e, captcha != null);
            throw new UserErrorException(message);
        } catch (NonNormalizedPhoneNumberException e) {
            throw new UserErrorException("Failed to change number: " + e.getMessage(), e);
        } catch (NotPrimaryDeviceException e) {
            throw new UserErrorException("This command doesn't work on linked devices.");
        } catch (IOException e) {
            throw new IOErrorException("Failed to change number: %s (%s)".formatted(e.getMessage(),
                    e.getClass().getSimpleName()), e);
        }
    }
}
