package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.function.Supplier;

public interface RecipientResolver {

    RecipientId resolveRecipient(RecipientAddress address);

    RecipientId resolveRecipient(long recipientId);

    RecipientId resolveRecipient(String identifier);

    default RecipientId resolveRecipient(SignalServiceAddress address) {
        return resolveRecipient(new RecipientAddress(address));
    }

    RecipientId resolveRecipient(ServiceId serviceId);

    class RecipientResolverWrapper implements RecipientResolver {

        private final Supplier<RecipientResolver> recipientResolverSupplier;

        public RecipientResolverWrapper(final Supplier<RecipientResolver> recipientResolverSupplier) {
            this.recipientResolverSupplier = recipientResolverSupplier;
        }

        @Override
        public RecipientId resolveRecipient(final RecipientAddress address) {
            return recipientResolverSupplier.get().resolveRecipient(address);
        }

        @Override
        public RecipientId resolveRecipient(final long recipientId) {
            return recipientResolverSupplier.get().resolveRecipient(recipientId);
        }

        @Override
        public RecipientId resolveRecipient(final String identifier) {
            return recipientResolverSupplier.get().resolveRecipient(identifier);
        }

        @Override
        public RecipientId resolveRecipient(final ServiceId serviceId) {
            return recipientResolverSupplier.get().resolveRecipient(serviceId);
        }
    }
}
