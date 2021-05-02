package org.asamk.signal.manager.util;

import org.asamk.signal.manager.storage.recipients.Profile;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.util.Base64;
import java.util.Date;
import java.util.HashSet;

public class ProfileUtils {

    public static Profile decryptProfile(
            final ProfileKey profileKey, final SignalServiceProfile encryptedProfile
    ) {
        var profileCipher = new ProfileCipher(profileKey);
        try {
            var name = decryptName(encryptedProfile.getName(), profileCipher);
            var about = decryptName(encryptedProfile.getAbout(), profileCipher);
            var aboutEmoji = decryptName(encryptedProfile.getAboutEmoji(), profileCipher);
            String unidentifiedAccess;
            try {
                unidentifiedAccess = encryptedProfile.getUnidentifiedAccess() == null
                        || !profileCipher.verifyUnidentifiedAccess(Base64.getDecoder()
                        .decode(encryptedProfile.getUnidentifiedAccess()))
                        ? null
                        : encryptedProfile.getUnidentifiedAccess();
            } catch (IllegalArgumentException e) {
                unidentifiedAccess = null;
            }
            final var nameParts = splitName(name);
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
            return new Profile(new Date().getTime(),
                    nameParts.first(),
                    nameParts.second(),
                    about,
                    aboutEmoji,
                    encryptedProfile.isUnrestrictedUnidentifiedAccess()
                            ? Profile.UnidentifiedAccessMode.UNRESTRICTED
                            : unidentifiedAccess != null
                                    ? Profile.UnidentifiedAccessMode.ENABLED
                                    : Profile.UnidentifiedAccessMode.DISABLED,
                    capabilities);
        } catch (InvalidCiphertextException e) {
            return null;
        }
    }

    private static String decryptName(
            final String encryptedName, final ProfileCipher profileCipher
    ) throws InvalidCiphertextException {
        try {
            return encryptedName == null
                    ? null
                    : new String(profileCipher.decryptName(Base64.getDecoder().decode(encryptedName)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Pair<String, String> splitName(String name) {
        if (name == null) {
            return new Pair<>(null, null);
        }
        String[] parts = name.split("\0");

        switch (parts.length) {
            case 0:
                return new Pair<>(null, null);
            case 1:
                return new Pair<>(parts[0], null);
            default:
                return new Pair<>(parts[0], parts[1]);
        }
    }
}
