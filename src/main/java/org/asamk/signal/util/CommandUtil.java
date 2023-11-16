package org.asamk.signal.util;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.CaptchaRequiredException;
import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdFormatException;
import org.asamk.signal.manager.api.InvalidNumberException;
import org.asamk.signal.manager.api.RateLimitException;
import org.asamk.signal.manager.api.ReceiveConfig;
import org.asamk.signal.manager.api.RecipientIdentifier;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommandUtil {

    private CommandUtil() {
    }

    public static Set<RecipientIdentifier> getRecipientIdentifiers(
            final Manager m,
            final boolean isNoteToSelf,
            final List<String> recipientStrings,
            final List<String> groupIdStrings,
            final List<String> usernameStrings
    ) throws UserErrorException {
        final var recipientIdentifiers = new HashSet<RecipientIdentifier>();
        if (isNoteToSelf) {
            recipientIdentifiers.add(RecipientIdentifier.NoteToSelf.INSTANCE);
        }
        if (recipientStrings != null) {
            final var localNumber = m.getSelfNumber();
            recipientIdentifiers.addAll(CommandUtil.getSingleRecipientIdentifiers(recipientStrings, localNumber));
        }
        if (groupIdStrings != null) {
            recipientIdentifiers.addAll(CommandUtil.getGroupIdentifiers(groupIdStrings));
        }
        if (usernameStrings != null) {
            recipientIdentifiers.addAll(CommandUtil.getUsernameIdentifiers(usernameStrings));
        }

        if (recipientIdentifiers.isEmpty()) {
            throw new UserErrorException("No recipients given");
        }
        return recipientIdentifiers;
    }

    public static Set<RecipientIdentifier.Group> getGroupIdentifiers(Collection<String> groupIdStrings) throws UserErrorException {
        if (groupIdStrings == null) {
            return Set.of();
        }
        final var groupIds = new HashSet<RecipientIdentifier.Group>();
        for (final var groupIdString : groupIdStrings) {
            groupIds.add(new RecipientIdentifier.Group(getGroupId(groupIdString)));
        }
        return groupIds;
    }

    public static Set<GroupId> getGroupIds(Collection<String> groupIdStrings) throws UserErrorException {
        if (groupIdStrings == null) {
            return Set.of();
        }
        final var groupIds = new HashSet<GroupId>();
        for (final var groupIdString : groupIdStrings) {
            groupIds.add(getGroupId(groupIdString));
        }
        return groupIds;
    }

    public static GroupId getGroupId(String groupId) throws UserErrorException {
        if (groupId == null) {
            return null;
        }
        try {
            return GroupId.fromBase64(groupId);
        } catch (GroupIdFormatException e) {
            throw new UserErrorException("Invalid group id: " + e.getMessage());
        }
    }

    public static Set<RecipientIdentifier.Single> getSingleRecipientIdentifiers(
            final Collection<String> recipientStrings, final String localNumber
    ) throws UserErrorException {
        if (recipientStrings == null) {
            return Set.of();
        }
        final var identifiers = new HashSet<RecipientIdentifier.Single>();
        for (var recipientString : recipientStrings) {
            identifiers.add(getSingleRecipientIdentifier(recipientString, localNumber));
        }
        return identifiers;
    }

    public static RecipientIdentifier.Single getSingleRecipientIdentifier(
            final String recipientString, final String localNumber
    ) throws UserErrorException {
        try {
            return RecipientIdentifier.Single.fromString(recipientString, localNumber);
        } catch (InvalidNumberException e) {
            throw new UserErrorException("Invalid phone number '" + recipientString + "': " + e.getMessage(), e);
        }
    }

    public static Set<RecipientIdentifier.Username> getUsernameIdentifiers(Collection<String> usernameIdStrings) {
        if (usernameIdStrings == null) {
            return Set.of();
        }
        final var usernameIds = new HashSet<RecipientIdentifier.Username>();
        for (final var usernameIdString : usernameIdStrings) {
            usernameIds.add(new RecipientIdentifier.Username(usernameIdString));
        }
        return usernameIds;
    }

    public static String getCaptchaRequiredMessage(final CaptchaRequiredException e, final boolean captchaProvided) {
        String message;
        if (!captchaProvided) {
            message = """
                      Captcha required for verification, use --captcha CAPTCHA
                      To get the token, go to https://signalcaptchas.org/registration/generate.html
                      After solving the captcha, right-click on the "Open Signal" link and copy the link.""";
        } else {
            message = "Invalid captcha given.";
        }
        if (e.getNextAttemptTimestamp() > 0) {
            message += "\nNext Captcha may be provided at " + DateUtils.formatTimestamp(e.getNextAttemptTimestamp());
        }
        return message;
    }

    public static String getRateLimitMessage(final RateLimitException e) {
        String message = "Rate limit reached";
        if (e.getNextAttemptTimestamp() > 0) {
            message += "\nNext attempt may be tried at " + DateUtils.formatTimestamp(e.getNextAttemptTimestamp());
        }
        return message;
    }

    public static ReceiveConfig getReceiveConfig(final Namespace ns) {
        final var ignoreAttachments = Boolean.TRUE.equals(ns.getBoolean("ignore-attachments"));
        final var ignoreStories = Boolean.TRUE.equals(ns.getBoolean("ignore-stories"));
        final var sendReadReceipts = Boolean.TRUE.equals(ns.getBoolean("send-read-receipts"));

        return new ReceiveConfig(ignoreAttachments, ignoreStories, sendReadReceipts);
    }
}
