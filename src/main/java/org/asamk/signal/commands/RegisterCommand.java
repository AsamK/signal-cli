package org.asamk.signal.commands;

import com.fasterxml.jackson.core.type.TypeReference;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.RateLimitErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.NonNormalizedPhoneNumberException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.TotpRequiredException;
import org.asamk.signal.manager.api.VerificationMethodNotAvailableException;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.util.CommandUtil;

import java.io.IOException;
import java.util.List;
import java.util.SequencedCollection;

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
        subparser.addArgument("--reregister")
                .action(Arguments.storeTrue())
                .help("Register even if account is already registered");
        subparser.addArgument("--recovery-key").help("Recover an account using its 64-character Signal Recovery Key.");
        subparser.addArgument("--totp").help("A six-digit TOTP token required for account recovery.");
    }

    @Override
    public void handleCommand(final Namespace ns, final RegistrationManager m) throws CommandException {
        final boolean voiceVerification = Boolean.TRUE.equals(ns.getBoolean("voice"));
        final var captcha = ns.getString("captcha");
        final var reregister = Boolean.TRUE.equals(ns.getBoolean("reregister"));
        final var recoveryKey = ns.getString("recovery-key");
        final var totp = ns.getString("totp");

        register(m, voiceVerification, captcha, reregister, recoveryKey, totp);
    }

    @Override
    public TypeReference<RegistrationParams> getRequestType() {
        return new TypeReference<>() {};
    }

    @Override
    public SequencedCollection<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.PLAIN_TEXT, OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final RegistrationParams request,
            final RegistrationManager m,
            final JsonWriter jsonWriter
    ) throws CommandException {
        register(m,
                Boolean.TRUE.equals(request.voice()),
                request.captcha(),
                Boolean.TRUE.equals(request.reregister()),
                request.recoveryKey(),
                request.totp());
    }

    private void register(
            final RegistrationManager m,
            final boolean voiceVerification,
            final String captcha,
            final boolean reregister,
            final String recoveryKey,
            final String totpValue
    ) throws CommandException {
        if (recoveryKey == null && totpValue != null) {
            throw new UserErrorException("--totp requires --recovery-key");
        }

        final Integer totp;
        if (totpValue == null) {
            totp = null;
        } else if (!totpValue.matches("[0-9]{6}")) {
            throw new UserErrorException("TOTP token must contain exactly six digits");
        } else {
            totp = Integer.parseInt(totpValue);
        }

        if (recoveryKey != null) {
            if (voiceVerification || captcha != null) {
                throw new UserErrorException("--recovery-key cannot be combined with --voice or --captcha");
            }
            try {
                m.registerWithRecoveryKey(recoveryKey, reregister, totp);
            } catch (RateLimitException e) {
                final var message = CommandUtil.getRateLimitMessage(e);
                throw new RateLimitErrorException(message, e);
            } catch (TotpRequiredException e) {
                throw new UserErrorException("A TOTP token is required; rerun register with --totp TOKEN");
            } catch (IOException e) {
                throw new IOErrorException("Failed to register: %s (%s)".formatted(e.getMessage(),
                        e.getClass().getSimpleName()), e);
            }
            return;
        }

        try {
            m.register(voiceVerification, captcha, reregister);
        } catch (RateLimitException e) {
            final var message = CommandUtil.getRateLimitMessage(e);
            throw new RateLimitErrorException(message, e);
        } catch (CaptchaRequiredException e) {
            final var message = CommandUtil.getCaptchaRequiredMessage(e, captcha != null);
            throw new UserErrorException(message);
        } catch (NonNormalizedPhoneNumberException e) {
            throw new UserErrorException("Failed to register: " + e.getMessage(), e);
        } catch (TotpRequiredException e) {
            throw new UserErrorException(
                    "A TOTP token is required; rerun register with --recovery-key RECOVERY-KEY --totp TOKEN");
        } catch (IOException e) {
            throw new IOErrorException("Failed to register: %s (%s)".formatted(e.getMessage(),
                    e.getClass().getSimpleName()), e);
        } catch (VerificationMethodNotAvailableException e) {
            throw new UserErrorException("Failed to register: " + e.getMessage() + (
                    voiceVerification
                            ? ": Before requesting voice verification you need to request SMS verification and wait a minute."
                            : ""
            ), e);
        }
    }

    public record RegistrationParams(
            Boolean voice,
            String captcha,
            Boolean reregister,
            String recoveryKey,
            String totp
    ) {}
}
