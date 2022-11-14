package org.asamk.signal.manager.api;

import org.asamk.signal.manager.groups.GroupLinkState;
import org.asamk.signal.manager.groups.GroupPermission;

import java.util.Set;

public class UpdateGroup {

    private final String name;
    private final String description;
    private final Set<RecipientIdentifier.Single> members;
    private final Set<RecipientIdentifier.Single> removeMembers;
    private final Set<RecipientIdentifier.Single> admins;
    private final Set<RecipientIdentifier.Single> removeAdmins;
    private final Set<RecipientIdentifier.Single> banMembers;
    private final Set<RecipientIdentifier.Single> unbanMembers;
    private final boolean resetGroupLink;
    private final GroupLinkState groupLinkState;
    private final GroupPermission addMemberPermission;
    private final GroupPermission editDetailsPermission;
    private final String avatarFile;
    private final Integer expirationTimer;
    private final Boolean isAnnouncementGroup;

    private UpdateGroup(final Builder builder) {
        name = builder.name;
        description = builder.description;
        members = builder.members;
        removeMembers = builder.removeMembers;
        admins = builder.admins;
        removeAdmins = builder.removeAdmins;
        banMembers = builder.banMembers;
        unbanMembers = builder.unbanMembers;
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
        return new Builder(copy.name,
                copy.description,
                copy.members,
                copy.removeMembers,
                copy.admins,
                copy.removeAdmins,
                copy.banMembers,
                copy.unbanMembers,
                copy.resetGroupLink,
                copy.groupLinkState,
                copy.addMemberPermission,
                copy.editDetailsPermission,
                copy.avatarFile,
                copy.expirationTimer,
                copy.isAnnouncementGroup);
    }

    public static Builder newBuilder(
            final String name,
            final String description,
            final Set<RecipientIdentifier.Single> members,
            final Set<RecipientIdentifier.Single> removeMembers,
            final Set<RecipientIdentifier.Single> admins,
            final Set<RecipientIdentifier.Single> removeAdmins,
            final Set<RecipientIdentifier.Single> banMembers,
            final Set<RecipientIdentifier.Single> unbanMembers,
            final boolean resetGroupLink,
            final GroupLinkState groupLinkState,
            final GroupPermission addMemberPermission,
            final GroupPermission editDetailsPermission,
            final String avatarFile,
            final Integer expirationTimer,
            final Boolean isAnnouncementGroup
    ) {
        return new Builder(name,
                description,
                members,
                removeMembers,
                admins,
                removeAdmins,
                banMembers,
                unbanMembers,
                resetGroupLink,
                groupLinkState,
                addMemberPermission,
                editDetailsPermission,
                avatarFile,
                expirationTimer,
                isAnnouncementGroup);
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

    public Set<RecipientIdentifier.Single> getBanMembers() {
        return banMembers;
    }

    public Set<RecipientIdentifier.Single> getUnbanMembers() {
        return unbanMembers;
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

    public String getAvatarFile() {
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
        private Set<RecipientIdentifier.Single> banMembers;
        private Set<RecipientIdentifier.Single> unbanMembers;
        private boolean resetGroupLink;
        private GroupLinkState groupLinkState;
        private GroupPermission addMemberPermission;
        private GroupPermission editDetailsPermission;
        private String avatarFile;
        private Integer expirationTimer;
        private Boolean isAnnouncementGroup;

        private Builder() {
        }

        private Builder(
                final String name,
                final String description,
                final Set<RecipientIdentifier.Single> members,
                final Set<RecipientIdentifier.Single> removeMembers,
                final Set<RecipientIdentifier.Single> admins,
                final Set<RecipientIdentifier.Single> removeAdmins,
                final Set<RecipientIdentifier.Single> banMembers,
                final Set<RecipientIdentifier.Single> unbanMembers,
                final boolean resetGroupLink,
                final GroupLinkState groupLinkState,
                final GroupPermission addMemberPermission,
                final GroupPermission editDetailsPermission,
                final String avatarFile,
                final Integer expirationTimer,
                final Boolean isAnnouncementGroup
        ) {
            this.name = name;
            this.description = description;
            this.members = members;
            this.removeMembers = removeMembers;
            this.admins = admins;
            this.removeAdmins = removeAdmins;
            this.banMembers = banMembers;
            this.unbanMembers = unbanMembers;
            this.resetGroupLink = resetGroupLink;
            this.groupLinkState = groupLinkState;
            this.addMemberPermission = addMemberPermission;
            this.editDetailsPermission = editDetailsPermission;
            this.avatarFile = avatarFile;
            this.expirationTimer = expirationTimer;
            this.isAnnouncementGroup = isAnnouncementGroup;
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

        public Builder withBanMembers(final Set<RecipientIdentifier.Single> val) {
            banMembers = val;
            return this;
        }

        public Builder withUnbanMembers(final Set<RecipientIdentifier.Single> val) {
            unbanMembers = val;
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

        public Builder withAvatarFile(final String val) {
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
