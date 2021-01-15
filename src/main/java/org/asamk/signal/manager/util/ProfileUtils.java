package org.asamk.signal.manager.util;

import org.asamk.signal.manager.storage.profiles.SignalProfile;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.util.Base64;

import java.io.IOException;

public class ProfileUtils {

    public static SignalProfile decryptProfile(
            final ProfileKey profileKey, final SignalServiceProfile encryptedProfile
    ) {
        ProfileCipher profileCipher = new ProfileCipher(profileKey);
        try {
            String name;
            try {
                name = encryptedProfile.getName() == null
                        ? null
                        : new String(profileCipher.decryptName(Base64.decode(encryptedProfile.getName())));
            } catch (IOException e) {
                name = null;
            }
            String unidentifiedAccess;
            try {
                unidentifiedAccess = encryptedProfile.getUnidentifiedAccess() == null
                        || !profileCipher.verifyUnidentifiedAccess(Base64.decode(encryptedProfile.getUnidentifiedAccess()))
                        ? null
                        : encryptedProfile.getUnidentifiedAccess();
            } catch (IOException e) {
                unidentifiedAccess = null;
            }
            return new SignalProfile(encryptedProfile.getIdentityKey(),
                    name,
                    unidentifiedAccess,
                    encryptedProfile.isUnrestrictedUnidentifiedAccess(),
                    encryptedProfile.getCapabilities());
        } catch (InvalidCiphertextException e) {
            return null;
        }
    }
}
