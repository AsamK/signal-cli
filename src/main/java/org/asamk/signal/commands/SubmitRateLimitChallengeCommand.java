package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;

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
        final var captchaString = ns.getString("captcha");
        final var captcha = captchaString == null ? null : captchaString.replace("signalcaptcha://", "");

        try {
            m.submitRateLimitRecaptchaChallenge(challenge, captcha);
        } catch (IOException e) {
            throw new IOErrorException("Submit challenge error: " + e.getMessage(), e);
        }
    }
}
