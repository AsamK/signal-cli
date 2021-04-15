package org.asamk.signal.manager.storage.recipients;

public class RecipientId {

    private final long id;

    RecipientId(final long id) {
        this.id = id;
    }

    public static RecipientId of(long id) {
        return new RecipientId(id);
    }

    public long getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final RecipientId that = (RecipientId) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
