package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class LegacyJsonIdentityKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacyJsonIdentityKeyStore.class);

    private final List<LegacyIdentityInfo> identities;
    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;

    private LegacyJsonIdentityKeyStore(
            final List<LegacyIdentityInfo> identities, IdentityKeyPair identityKeyPair, int localRegistrationId
    ) {
        this.identities = identities;
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
    }

    public List<LegacyIdentityInfo> getIdentities() {
        return identities.stream()
                .map(LegacyIdentityInfo::getAddress)
                .collect(Collectors.toSet())
                .stream()
                .map(this::getIdentity)
                .collect(Collectors.toList());
    }

    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    private LegacyIdentityInfo getIdentity(SignalServiceAddress serviceAddress) {
        long maxDate = 0;
        LegacyIdentityInfo maxIdentity = null;
        for (var id : this.identities) {
            if (!id.address.matches(serviceAddress)) {
                continue;
            }

            final var time = id.getDateAdded().getTime();
            if (maxIdentity == null || maxDate <= time) {
                maxDate = time;
                maxIdentity = id;
            }
        }
        return maxIdentity;
    }

    public static class JsonIdentityKeyStoreDeserializer extends JsonDeserializer<LegacyJsonIdentityKeyStore> {

        @Override
        public LegacyJsonIdentityKeyStore deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var localRegistrationId = node.get("registrationId").asInt();
            var identityKeyPair = new IdentityKeyPair(Base64.getDecoder().decode(node.get("identityKey").asText()));

            var identities = new ArrayList<LegacyIdentityInfo>();

            var trustedKeysNode = node.get("trustedKeys");
            if (trustedKeysNode.isArray()) {
                for (var trustedKey : trustedKeysNode) {
                    var trustedKeyName = trustedKey.hasNonNull("name") ? trustedKey.get("name").asText() : null;

                    if (UuidUtil.isUuid(trustedKeyName)) {
                        // Ignore identities that were incorrectly created with UUIDs as name
                        continue;
                    }

                    var uuid = trustedKey.hasNonNull("uuid")
                            ? UuidUtil.parseOrNull(trustedKey.get("uuid").asText())
                            : null;
                    final var serviceAddress = uuid == null
                            ? Utils.getSignalServiceAddressFromIdentifier(trustedKeyName)
                            : new SignalServiceAddress(uuid, trustedKeyName);
                    try {
                        var id = new IdentityKey(Base64.getDecoder().decode(trustedKey.get("identityKey").asText()), 0);
                        var trustLevel = trustedKey.hasNonNull("trustLevel") ? TrustLevel.fromInt(trustedKey.get(
                                "trustLevel").asInt()) : TrustLevel.TRUSTED_UNVERIFIED;
                        var added = trustedKey.hasNonNull("addedTimestamp") ? new Date(trustedKey.get("addedTimestamp")
                                .asLong()) : new Date();
                        identities.add(new LegacyIdentityInfo(serviceAddress, id, trustLevel, added));
                    } catch (InvalidKeyException e) {
                        logger.warn("Error while decoding key for {}: {}", trustedKeyName, e.getMessage());
                    }
                }
            }

            return new LegacyJsonIdentityKeyStore(identities, identityKeyPair, localRegistrationId);
        }
    }
}
