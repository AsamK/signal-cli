package org.asamk.signal.manager.storage.recipients;

/**
 * Test helper to create {@link RecipientId} instances without a RecipientStore.
 */
public class TestRecipientIdFactory {

    public static RecipientId create(long id) {
        return new RecipientId(id, null);
    }
}
