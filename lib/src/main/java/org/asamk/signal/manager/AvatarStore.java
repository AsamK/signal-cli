package org.asamk.signal.manager;

import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.util.IOUtils;
import org.asamk.signal.manager.util.Utils;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class AvatarStore {

    private final File avatarsPath;

    public AvatarStore(final File avatarsPath) {
        this.avatarsPath = avatarsPath;
    }

    public StreamDetails retrieveContactAvatar(SignalServiceAddress address) throws IOException {
        return retrieveAvatar(getContactAvatarFile(address));
    }

    public StreamDetails retrieveProfileAvatar(SignalServiceAddress address) throws IOException {
        return retrieveAvatar(getProfileAvatarFile(address));
    }

    public StreamDetails retrieveGroupAvatar(GroupId groupId) throws IOException {
        final var groupAvatarFile = getGroupAvatarFile(groupId);
        return retrieveAvatar(groupAvatarFile);
    }

    public void storeContactAvatar(SignalServiceAddress address, AvatarStorer storer) throws IOException {
        storeAvatar(getContactAvatarFile(address), storer);
    }

    public void storeProfileAvatar(SignalServiceAddress address, AvatarStorer storer) throws IOException {
        storeAvatar(getProfileAvatarFile(address), storer);
    }

    public void storeGroupAvatar(GroupId groupId, AvatarStorer storer) throws IOException {
        storeAvatar(getGroupAvatarFile(groupId), storer);
    }

    public void deleteProfileAvatar(SignalServiceAddress address) throws IOException {
        deleteAvatar(getProfileAvatarFile(address));
    }

    private StreamDetails retrieveAvatar(final File avatarFile) throws IOException {
        if (!avatarFile.exists()) {
            return null;
        }
        return Utils.createStreamDetailsFromFile(avatarFile);
    }

    private void storeAvatar(final File avatarFile, final AvatarStorer storer) throws IOException {
        createAvatarsDir();
        try (OutputStream output = new FileOutputStream(avatarFile)) {
            storer.store(output);
        }
    }

    private void deleteAvatar(final File avatarFile) throws IOException {
        if (avatarFile.exists()) {
            Files.delete(avatarFile.toPath());
        }
    }

    private File getGroupAvatarFile(GroupId groupId) {
        return new File(avatarsPath, "group-" + groupId.toBase64().replace("/", "_"));
    }

    private File getContactAvatarFile(SignalServiceAddress address) {
        return new File(avatarsPath, "contact-" + getLegacyIdentifier(address));
    }

    private String getLegacyIdentifier(final SignalServiceAddress address) {
        return address.getNumber().or(() -> address.getUuid().get().toString());
    }

    private File getProfileAvatarFile(SignalServiceAddress address) {
        return new File(avatarsPath, "profile-" + getLegacyIdentifier(address));
    }

    private void createAvatarsDir() throws IOException {
        IOUtils.createPrivateDirectories(avatarsPath);
    }

    @FunctionalInterface
    public interface AvatarStorer {

        void store(OutputStream outputStream) throws IOException;
    }
}
