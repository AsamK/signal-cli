package org.asamk.signal.manager.storage.recipients;

import java.util.Objects;

public final class RecipientId {

    private long id;
    private final RecipientStore recipientStore;

    RecipientId(long id, final RecipientStore recipientStore) {
        this.id = id;
        this.recipientStore = recipientStore;
    }

    public long id() {
        if (recipientStore != null) {
            final var actualRecipientId = recipientStore.getActualRecipientId(this.id);
            if (actualRecipientId != this.id) {
                this.id = actualRecipientId;
            }
        }
        return this.id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RecipientId) obj;
        return this.id() == that.id();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id());
    }

    @Override
    public String toString() {
        return "RecipientId[" + "id=" + id() + ']';
    }
}
