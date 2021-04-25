package org.asamk.signal.manager.util;

import org.asamk.signal.manager.storage.profiles.SignalProfile;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.util.Base64;

public class ProfileUtils {

    public static SignalProfile decryptProfile(
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
            return new SignalProfile(encryptedProfile.getIdentityKey(),
                    name,
                    about,
                    aboutEmoji,
                    unidentifiedAccess,
                    encryptedProfile.isUnrestrictedUnidentifiedAccess(),
                    encryptedProfile.getCapabilities());
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
}
