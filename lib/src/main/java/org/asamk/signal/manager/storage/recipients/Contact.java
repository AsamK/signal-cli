package org.asamk.signal.manager.storage.recipients;

public class Contact {

    private final String name;

    private final String color;

    private final int messageExpirationTime;

    private final boolean blocked;

    private final boolean archived;

    public Contact(
            final String name,
            final String color,
            final int messageExpirationTime,
            final boolean blocked,
            final boolean archived
    ) {
        this.name = name;
        this.color = color;
        this.messageExpirationTime = messageExpirationTime;
        this.blocked = blocked;
        this.archived = archived;
    }

    private Contact(final Builder builder) {
        name = builder.name;
        color = builder.color;
        messageExpirationTime = builder.messageExpirationTime;
        blocked = builder.blocked;
        archived = builder.archived;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder newBuilder(final Contact copy) {
        Builder builder = new Builder();
        builder.name = copy.getName();
        builder.color = copy.getColor();
        builder.messageExpirationTime = copy.getMessageExpirationTime();
        builder.blocked = copy.isBlocked();
        builder.archived = copy.isArchived();
        return builder;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public int getMessageExpirationTime() {
        return messageExpirationTime;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isArchived() {
        return archived;
    }

    public static final class Builder {

        private String name;
        private String color;
        private int messageExpirationTime;
        private boolean blocked;
        private boolean archived;

        private Builder() {
        }

        public Builder withName(final String val) {
            name = val;
            return this;
        }

        public Builder withColor(final String val) {
            color = val;
            return this;
        }

        public Builder withMessageExpirationTime(final int val) {
            messageExpirationTime = val;
            return this;
        }

        public Builder withBlocked(final boolean val) {
            blocked = val;
            return this;
        }

        public Builder withArchived(final boolean val) {
            archived = val;
            return this;
        }

        public Contact build() {
            return new Contact(this);
        }
    }
}
