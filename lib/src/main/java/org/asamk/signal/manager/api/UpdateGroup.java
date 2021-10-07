package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupPermission;

import java.io.File;
import java.util.Set;

public class UpdateGroup {

    private final String name;
    private final String description;
    private final Set<RecipientIdentifier.Single> members;
    private final Set<RecipientIdentifier.Single> removeMembers;
    private final Set<RecipientIdentifier.Single> admins;
    private final Set<RecipientIdentifier.Single> removeAdmins;
    private final boolean resetGroupLink;
    private final GroupLinkState groupLinkState;
    private final GroupPermission addMemberPermission;
    private final GroupPermission editDetailsPermission;
    private final File avatarFile;
    private final Integer expirationTimer;
    private final Boolean isAnnouncementGroup;

    private UpdateGroup(final Builder builder) {
        name = builder.name;
        description = builder.description;
        members = builder.members;
        removeMembers = builder.removeMembers;
        admins = builder.admins;
        removeAdmins = builder.removeAdmins;
        resetGroupLink = builder.resetGroupLink;
        groupLinkState = builder.groupLinkState;
        addMemberPermission = builder.addMemberPermission;
        editDetailsPermission = builder.editDetailsPermission;
        avatarFile = builder.avatarFile;
        expirationTimer = builder.expirationTimer;
        isAnnouncementGroup = builder.isAnnouncementGroup;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final UpdateGroup copy) {
        Builder builder = new Builder();
        builder.name = copy.getName();
        builder.description = copy.getDescription();
        builder.members = copy.getMembers();
        builder.removeMembers = copy.getRemoveMembers();
        builder.admins = copy.getAdmins();
        builder.removeAdmins = copy.getRemoveAdmins();
        builder.resetGroupLink = copy.isResetGroupLink();
        builder.groupLinkState = copy.getGroupLinkState();
        builder.addMemberPermission = copy.getAddMemberPermission();
        builder.editDetailsPermission = copy.getEditDetailsPermission();
        builder.avatarFile = copy.getAvatarFile();
        builder.expirationTimer = copy.getExpirationTimer();
        builder.isAnnouncementGroup = copy.getIsAnnouncementGroup();
        return builder;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<RecipientIdentifier.Single> getMembers() {
        return members;
    }

    public Set<RecipientIdentifier.Single> getRemoveMembers() {
        return removeMembers;
    }

    public Set<RecipientIdentifier.Single> getAdmins() {
        return admins;
    }

    public Set<RecipientIdentifier.Single> getRemoveAdmins() {
        return removeAdmins;
    }

    public boolean isResetGroupLink() {
        return resetGroupLink;
    }

    public GroupLinkState getGroupLinkState() {
        return groupLinkState;
    }

    public GroupPermission getAddMemberPermission() {
        return addMemberPermission;
    }

    public GroupPermission getEditDetailsPermission() {
        return editDetailsPermission;
    }

    public File getAvatarFile() {
        return avatarFile;
    }

    public Integer getExpirationTimer() {
        return expirationTimer;
    }

    public Boolean getIsAnnouncementGroup() {
        return isAnnouncementGroup;
    }

    public static final class Builder {

        private String name;
        private String description;
        private Set<RecipientIdentifier.Single> members;
        private Set<RecipientIdentifier.Single> removeMembers;
        private Set<RecipientIdentifier.Single> admins;
        private Set<RecipientIdentifier.Single> removeAdmins;
        private boolean resetGroupLink;
        private GroupLinkState groupLinkState;
        private GroupPermission addMemberPermission;
        private GroupPermission editDetailsPermission;
        private File avatarFile;
        private Integer expirationTimer;
        private Boolean isAnnouncementGroup;

        private Builder() {
        }

        public Builder withName(final String val) {
            name = val;
            return this;
        }

        public Builder withDescription(final String val) {
            description = val;
            return this;
        }

        public Builder withMembers(final Set<RecipientIdentifier.Single> val) {
            members = val;
            return this;
        }

        public Builder withRemoveMembers(final Set<RecipientIdentifier.Single> val) {
            removeMembers = val;
            return this;
        }

        public Builder withAdmins(final Set<RecipientIdentifier.Single> val) {
            admins = val;
            return this;
        }

        public Builder withRemoveAdmins(final Set<RecipientIdentifier.Single> val) {
            removeAdmins = val;
            return this;
        }

        public Builder withResetGroupLink(final boolean val) {
            resetGroupLink = val;
            return this;
        }

        public Builder withGroupLinkState(final GroupLinkState val) {
            groupLinkState = val;
            return this;
        }

        public Builder withAddMemberPermission(final GroupPermission val) {
            addMemberPermission = val;
            return this;
        }

        public Builder withEditDetailsPermission(final GroupPermission val) {
            editDetailsPermission = val;
            return this;
        }

        public Builder withAvatarFile(final File val) {
            avatarFile = val;
            return this;
        }

        public Builder withExpirationTimer(final Integer val) {
            expirationTimer = val;
            return this;
        }

        public Builder withIsAnnouncementGroup(final Boolean val) {
            isAnnouncementGroup = val;
            return this;
        }

        public UpdateGroup build() {
            return new UpdateGroup(this);
        }
    }
}
