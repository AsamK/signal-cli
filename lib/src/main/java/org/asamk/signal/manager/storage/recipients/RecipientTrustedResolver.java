package org.asamk.signal.manager.storage.recipients;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Optional;
import java.util.function.Supplier;

public interface RecipientTrustedResolver {

    RecipientId resolveSelfRecipientTrusted(RecipientAddress address);

    RecipientId resolveRecipientTrusted(SignalServiceAddress address);

    RecipientId resolveRecipientTrusted(Optional<ACI> aci, Optional<PNI> pni, Optional<String> number);

    RecipientId resolveRecipientTrusted(ACI aci, String username);

    class RecipientTrustedResolverWrapper implements RecipientTrustedResolver {

        private final Supplier<RecipientTrustedResolver> recipientTrustedResolverSupplier;

        public RecipientTrustedResolverWrapper(final Supplier<RecipientTrustedResolver> recipientTrustedResolverSupplier) {
            this.recipientTrustedResolverSupplier = recipientTrustedResolverSupplier;
        }

        @Override
        public RecipientId resolveSelfRecipientTrusted(final RecipientAddress address) {
            return recipientTrustedResolverSupplier.get().resolveSelfRecipientTrusted(address);
        }

        @Override
        public RecipientId resolveRecipientTrusted(final SignalServiceAddress address) {
            return recipientTrustedResolverSupplier.get().resolveRecipientTrusted(address);
        }

        @Override
        public RecipientId resolveRecipientTrusted(
                final Optional<ACI> aci, final Optional<PNI> pni, final Optional<String> number
        ) {
            return recipientTrustedResolverSupplier.get().resolveRecipientTrusted(aci, pni, number);
        }

        @Override
        public RecipientId resolveRecipientTrusted(final ACI aci, final String username) {
            return recipientTrustedResolverSupplier.get().resolveRecipientTrusted(aci, username);
        }
    }
}
