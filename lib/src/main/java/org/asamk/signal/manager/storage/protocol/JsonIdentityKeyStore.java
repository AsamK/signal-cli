package org.asamk.signal.manager.storage.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.asamk.signal.manager.TrustLevel;
import org.asamk.signal.manager.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public class JsonIdentityKeyStore implements IdentityKeyStore {

    private final static Logger logger = LoggerFactory.getLogger(JsonIdentityKeyStore.class);

    private final List<IdentityInfo> identities = new ArrayList<>();

    private final IdentityKeyPair identityKeyPair;
    private final int localRegistrationId;

    private SignalServiceAddressResolver resolver;

    public JsonIdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
        this.identityKeyPair = identityKeyPair;
        this.localRegistrationId = localRegistrationId;
    }

    public void setResolver(final SignalServiceAddressResolver resolver) {
        this.resolver = resolver;
    }

    private SignalServiceAddress resolveSignalServiceAddress(String identifier) {
        if (resolver != null) {
            return resolver.resolveSignalServiceAddress(identifier);
        } else {
            return Utils.getSignalServiceAddressFromIdentifier(identifier);
        }
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return localRegistrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        return saveIdentity(resolveSignalServiceAddress(address.getName()),
                identityKey,
                TrustLevel.TRUSTED_UNVERIFIED,
                null);
    }

    /**
     * Adds the given identityKey for the user name and sets the trustLevel and added timestamp.
     * If the identityKey already exists, the trustLevel and added timestamp are NOT updated.
     *
     * @param serviceAddress User address, i.e. phone number and/or uuid
     * @param identityKey    The user's public key
     * @param trustLevel     Level of trust: untrusted, trusted, trusted and verified
     * @param added          Added timestamp, if null and the key is newly added, the current time is used.
     */
    public boolean saveIdentity(
            SignalServiceAddress serviceAddress, IdentityKey identityKey, TrustLevel trustLevel, Date added
    ) {
        for (var id : identities) {
            if (!id.address.matches(serviceAddress) || !id.identityKey.equals(identityKey)) {
                continue;
            }

            if (!id.address.getUuid().isPresent() || !id.address.getNumber().isPresent()) {
                id.address = serviceAddress;
            }
            // Identity already exists, not updating the trust level
            return true;
        }

        identities.add(new IdentityInfo(serviceAddress, identityKey, trustLevel, added != null ? added : new Date()));
        return false;
    }

    /**
     * Update trustLevel for the given identityKey for the user name.
     *
     * @param serviceAddress User address, i.e. phone number and/or uuid
     * @param identityKey    The user's public key
     * @param trustLevel     Level of trust: untrusted, trusted, trusted and verified
     */
    public void setIdentityTrustLevel(
            SignalServiceAddress serviceAddress, IdentityKey identityKey, TrustLevel trustLevel
    ) {
        for (var id : identities) {
            if (!id.address.matches(serviceAddress) || !id.identityKey.equals(identityKey)) {
                continue;
            }

            if (!id.address.getUuid().isPresent() || !id.address.getNumber().isPresent()) {
                id.address = serviceAddress;
            }
            id.trustLevel = trustLevel;
            return;
        }

        identities.add(new IdentityInfo(serviceAddress, identityKey, trustLevel, new Date()));
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        // TODO implement possibility for different handling of incoming/outgoing trust decisions
        var serviceAddress = resolveSignalServiceAddress(address.getName());
        var trustOnFirstUse = true;

        for (var id : identities) {
            if (!id.address.matches(serviceAddress)) {
                continue;
            }

            if (id.identityKey.equals(identityKey)) {
                return id.isTrusted();
            } else {
                trustOnFirstUse = false;
            }
        }

        return trustOnFirstUse;
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        var serviceAddress = resolveSignalServiceAddress(address.getName());
        var identity = getIdentity(serviceAddress);
        return identity == null ? null : identity.getIdentityKey();
    }

    public IdentityInfo getIdentity(SignalServiceAddress serviceAddress) {
        long maxDate = 0;
        IdentityInfo maxIdentity = null;
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

    public List<IdentityInfo> getIdentities() {
        // TODO deep copy
        return identities;
    }

    public List<IdentityInfo> getIdentities(SignalServiceAddress serviceAddress) {
        var identities = new ArrayList<IdentityInfo>();
        for (var identity : this.identities) {
            if (identity.address.matches(serviceAddress)) {
                identities.add(identity);
            }
        }
        return identities;
    }

    public static class JsonIdentityKeyStoreDeserializer extends JsonDeserializer<JsonIdentityKeyStore> {

        @Override
        public JsonIdentityKeyStore deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            var localRegistrationId = node.get("registrationId").asInt();
            var identityKeyPair = new IdentityKeyPair(Base64.getDecoder().decode(node.get("identityKey").asText()));

            var keyStore = new JsonIdentityKeyStore(identityKeyPair, localRegistrationId);

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
                        keyStore.saveIdentity(serviceAddress, id, trustLevel, added);
                    } catch (InvalidKeyException e) {
                        logger.warn("Error while decoding key for {}: {}", trustedKeyName, e.getMessage());
                    }
                }
            }

            return keyStore;
        }
    }

    public static class JsonIdentityKeyStoreSerializer extends JsonSerializer<JsonIdentityKeyStore> {

        @Override
        public void serialize(
                JsonIdentityKeyStore jsonIdentityKeyStore, JsonGenerator json, SerializerProvider serializerProvider
        ) throws IOException {
            json.writeStartObject();
            json.writeNumberField("registrationId", jsonIdentityKeyStore.getLocalRegistrationId());
            json.writeStringField("identityKey",
                    Base64.getEncoder().encodeToString(jsonIdentityKeyStore.getIdentityKeyPair().serialize()));
            json.writeStringField("identityPrivateKey",
                    Base64.getEncoder()
                            .encodeToString(jsonIdentityKeyStore.getIdentityKeyPair().getPrivateKey().serialize()));
            json.writeStringField("identityPublicKey",
                    Base64.getEncoder()
                            .encodeToString(jsonIdentityKeyStore.getIdentityKeyPair().getPublicKey().serialize()));
            json.writeArrayFieldStart("trustedKeys");
            for (var trustedKey : jsonIdentityKeyStore.identities) {
                json.writeStartObject();
                if (trustedKey.getAddress().getNumber().isPresent()) {
                    json.writeStringField("name", trustedKey.getAddress().getNumber().get());
                }
                if (trustedKey.getAddress().getUuid().isPresent()) {
                    json.writeStringField("uuid", trustedKey.getAddress().getUuid().get().toString());
                }
                json.writeStringField("identityKey",
                        Base64.getEncoder().encodeToString(trustedKey.identityKey.serialize()));
                json.writeNumberField("trustLevel", trustedKey.trustLevel.ordinal());
                json.writeNumberField("addedTimestamp", trustedKey.added.getTime());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }
}
