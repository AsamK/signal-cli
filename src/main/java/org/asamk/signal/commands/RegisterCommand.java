package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.util.DateUtils;

import java.io.IOException;
import java.util.List;

public class RegisterCommand implements RegistrationCommand, JsonRpcRegistrationCommand<RegisterCommand.RegistrationParams> {

    @Override
    public String getName() {
        return "register";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Register a phone number with SMS or voice verification.");
        subparser.addArgument("-v", "--voice")
                .help("The verification should be done over voice, not SMS.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--captcha")
                .help("The captcha token, required if registration failed with a captcha required error.");
    }

    @Override
    public void handleCommand(final Namespace ns, final RegistrationManager m) throws CommandException {
        final boolean voiceVerification = Boolean.TRUE.equals(ns.getBoolean("voice"));
        final var captcha = ns.getString("captcha");

        register(m, voiceVerification, captcha);
    }

    @Override
    public TypeReference<RegistrationParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final RegistrationParams request, final RegistrationManager m, final JsonWriter jsonWriter
    ) throws CommandException {
        register(m, Boolean.TRUE.equals(request.voice()), request.captcha());
    }

    private void register(
            final RegistrationManager m, final boolean voiceVerification, final String captcha
    ) throws UserErrorException, IOErrorException {
        try {
            m.register(voiceVerification, captcha);
        } catch (RateLimitException e) {
            String message = "Rate limit reached";
            if (e.getNextAttemptTimestamp() > 0) {
                message += "\nNext attempt may be tried at " + DateUtils.formatTimestamp(e.getNextAttemptTimestamp());
            }
            throw new UserErrorException(message);
        } catch (CaptchaRequiredException e) {
            String message;
            if (captcha == null) {
                message = """
                          Captcha required for verification, use --captcha CAPTCHA
                          To get the token, go to https://signalcaptchas.org/registration/generate.html
                          Check the developer tools (F12) console for a failed redirect to signalcaptcha://
                          Everything after signalcaptcha:// is the captcha token.""";
            } else {
                message = "Invalid captcha given.";
            }
            if (e.getNextAttemptTimestamp() > 0) {
                message += "\nNext Captcha may be provided at "
                        + DateUtils.formatTimestamp(e.getNextAttemptTimestamp());
            }
            throw new UserErrorException(message);
        } catch (NonNormalizedPhoneNumberException e) {
            throw new UserErrorException("Failed to register: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IOErrorException("Failed to register: " + e.getMessage(), e);
        }
    }

    record RegistrationParams(Boolean voice, String captcha) {}
}
