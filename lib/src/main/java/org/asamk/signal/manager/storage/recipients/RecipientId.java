package org.asamk.signal.manager.storage.recipients;

public record RecipientId(long id) {

    public static RecipientId of(long id) {
        return new RecipientId(id);
    }
}
