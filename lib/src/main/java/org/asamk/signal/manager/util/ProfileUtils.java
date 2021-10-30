package org.asamk.signal.manager.util;

import org.asamk.signal.manager.storage.recipients.Profile;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.util.Base64;
import java.util.HashSet;

public class ProfileUtils {

    public static Profile decryptProfile(
            final ProfileKey profileKey, final SignalServiceProfile encryptedProfile
    ) {
        var profileCipher = new ProfileCipher(profileKey);
        try {
            var name = decrypt(encryptedProfile.getName(), profileCipher);
            var about = trimZeros(decrypt(encryptedProfile.getAbout(), profileCipher));
            var aboutEmoji = trimZeros(decrypt(encryptedProfile.getAboutEmoji(), profileCipher));

            final var nameParts = splitName(name);
            return new Profile(System.currentTimeMillis(),
                    nameParts.first(),
                    nameParts.second(),
                    about,
                    aboutEmoji,
                    encryptedProfile.getAvatar(),
                    getUnidentifiedAccessMode(encryptedProfile, profileCipher),
                    getCapabilities(encryptedProfile));
        } catch (InvalidCiphertextException e) {
            return null;
        }
    }

    public static Profile.UnidentifiedAccessMode getUnidentifiedAccessMode(
            final SignalServiceProfile encryptedProfile, final ProfileCipher profileCipher
    ) {
        if (encryptedProfile.isUnrestrictedUnidentifiedAccess()) {
            return Profile.UnidentifiedAccessMode.UNRESTRICTED;
        }

        if (encryptedProfile.getUnidentifiedAccess() != null && profileCipher != null) {
            final var unidentifiedAccessVerifier = Base64.getDecoder().decode(encryptedProfile.getUnidentifiedAccess());
            if (profileCipher.verifyUnidentifiedAccess(unidentifiedAccessVerifier)) {
                return Profile.UnidentifiedAccessMode.ENABLED;
            }
        }

        return Profile.UnidentifiedAccessMode.DISABLED;
    }

    public static HashSet<Profile.Capability> getCapabilities(final SignalServiceProfile encryptedProfile) {
        final var capabilities = new HashSet<Profile.Capability>();
        if (encryptedProfile.getCapabilities().isGv1Migration()) {
            capabilities.add(Profile.Capability.gv1Migration);
        }
        if (encryptedProfile.getCapabilities().isGv2()) {
            capabilities.add(Profile.Capability.gv2);
        }
        if (encryptedProfile.getCapabilities().isStorage()) {
            capabilities.add(Profile.Capability.storage);
        }
        if (encryptedProfile.getCapabilities().isSenderKey()) {
            capabilities.add(Profile.Capability.senderKey);
        }
        if (encryptedProfile.getCapabilities().isAnnouncementGroup()) {
            capabilities.add(Profile.Capability.announcementGroup);
        }

        return capabilities;
    }

    private static String decrypt(
            final String encryptedName, final ProfileCipher profileCipher
    ) throws InvalidCiphertextException {
        try {
            return encryptedName == null
                    ? null
                    : new String(profileCipher.decrypt(Base64.getDecoder().decode(encryptedName)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Pair<String, String> splitName(String name) {
        if (name == null) {
            return new Pair<>(null, null);
        }
        String[] parts = name.split("\0");

        return switch (parts.length) {
            case 0 -> new Pair<>(null, null);
            case 1 -> new Pair<>(parts[0], null);
            default -> new Pair<>(parts[0], parts[1]);
        };
    }

    static String trimZeros(String str) {
        if (str == null) {
            return null;
        }

        int pos = str.indexOf(0);
        return pos == -1 ? str : str.substring(0, pos);
    }
}
