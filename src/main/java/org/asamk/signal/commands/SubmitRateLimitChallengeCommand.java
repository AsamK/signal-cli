package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.CaptchaRejectedException;
import org.asamk.signal.output.OutputWriter;

import java.io.IOException;

public class SubmitRateLimitChallengeCommand implements JsonRpcLocalCommand {

    @Override
    public String getName() {
        return "submitRateLimitChallenge";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help(
                "Submit a captcha challenge to lift the rate limit. This command should only be necessary when sending fails with a proof required error.");
        subparser.addArgument("--challenge")
                .required(true)
                .help("The challenge token taken from the proof required error.");
        subparser.addArgument("--captcha")
                .required(true)
                .help("The captcha token from the solved captcha on the signal website.");
    }

    @Override
    public void handleCommand(final Namespace ns, final Manager m, OutputWriter outputWriter) throws CommandException {
        final var challenge = ns.getString("challenge");
        final var captcha = ns.getString("captcha");

        try {
            m.submitRateLimitRecaptchaChallenge(challenge, captcha);
        } catch (IOException e) {
            throw new IOErrorException("Submit challenge error: " + e.getMessage(), e);
        } catch (CaptchaRejectedException e) {
            throw new UserErrorException(
                    "Captcha rejected, it may be outdated, already used or solved from a different IP address.");
        }
    }
}
