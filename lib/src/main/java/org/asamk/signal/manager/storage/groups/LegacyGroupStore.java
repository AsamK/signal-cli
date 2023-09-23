package org.asamk.signal.manager.storage.groups;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.asamk.signal.manager.api.GroupId;
import org.asamk.signal.manager.api.GroupIdV1;
import org.asamk.signal.manager.api.GroupIdV2;
import org.asamk.signal.manager.storage.recipients.RecipientAddress;
import org.asamk.signal.manager.storage.recipients.RecipientResolver;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LegacyGroupStore {

    private final static Logger logger = LoggerFactory.getLogger(LegacyGroupStore.class);

    public static void migrate(
            final Storage storage,
            final File groupCachePath,
            final RecipientResolver recipientResolver,
            final GroupStore groupStore
    ) {
        final var groups = storage.groups.stream().map(g -> {
            if (g instanceof Storage.GroupV1 g1) {
                final var members = g1.members.stream().map(m -> {
                    if (m.recipientId == null) {
                        return recipientResolver.resolveRecipient(new RecipientAddress(ServiceId.parseOrNull(m.uuid),
                                m.number));
                    }

                    return recipientResolver.resolveRecipient(m.recipientId);
                }).filter(Objects::nonNull).collect(Collectors.toSet());

                return new GroupInfoV1(GroupIdV1.fromBase64(g1.groupId),
                        g1.expectedV2Id == null ? null : GroupIdV2.fromBase64(g1.expectedV2Id),
                        g1.name,
                        members,
                        g1.color,
                        g1.messageExpirationTime,
                        g1.blocked,
                        g1.archived);
            }

            final var g2 = (Storage.GroupV2) g;
            var groupId = GroupIdV2.fromBase64(g2.groupId);
            GroupMasterKey masterKey;
            try {
                masterKey = new GroupMasterKey(Base64.getDecoder().decode(g2.masterKey));
            } catch (InvalidInputException | IllegalArgumentException e) {
                throw new AssertionError("Invalid master key for group " + groupId.toBase64());
            }

            return new GroupInfoV2(groupId,
                    masterKey,
                    loadDecryptedGroupLocked(groupId, groupCachePath),
                    g2.distributionId == null ? DistributionId.create() : DistributionId.from(g2.distributionId),
                    g2.blocked,
                    g2.permissionDenied,
                    recipientResolver);
        }).toList();

        groupStore.addLegacyGroups(groups);
        removeGroupCache(groupCachePath);
    }

    private static void removeGroupCache(File groupCachePath) {
        final var files = groupCachePath.listFiles();
        if (files == null) {
            return;
        }

        for (var file : files) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                logger.error("Failed to delete group cache file {}: {}", file, e.getMessage());
            }
        }
        try {
            Files.delete(groupCachePath.toPath());
        } catch (IOException e) {
            logger.error("Failed to delete group cache directory {}: {}", groupCachePath, e.getMessage());
        }
    }

    private static DecryptedGroup loadDecryptedGroupLocked(final GroupIdV2 groupIdV2, final File groupCachePath) {
        var groupFile = getGroupV2File(groupIdV2, groupCachePath);
        if (!groupFile.exists()) {
            groupFile = getGroupV2FileLegacy(groupIdV2, groupCachePath);
        }
        if (!groupFile.exists()) {
            return null;
        }
        try (var stream = new FileInputStream(groupFile)) {
            return DecryptedGroup.ADAPTER.decode(stream);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static File getGroupV2FileLegacy(final GroupId groupId, final File groupCachePath) {
        return new File(groupCachePath, Hex.toStringCondensed(groupId.serialize()));
    }

    private static File getGroupV2File(final GroupId groupId, final File groupCachePath) {
        return new File(groupCachePath, groupId.toBase64().replace("/", "_"));
    }

    public record Storage(@JsonDeserialize(using = GroupsDeserializer.class) List<Record> groups) {

        public record GroupV1(
                String groupId,
                String expectedV2Id,
                String name,
                String color,
                int messageExpirationTime,
                boolean blocked,
                boolean archived,
                @JsonDeserialize(using = MembersDeserializer.class) List<Member> members
        ) {

            public record Member(Long recipientId, String uuid, String number) {}

            public record JsonRecipientAddress(String uuid, String number) {}

            private static class MembersDeserializer extends JsonDeserializer<List<Member>> {

                @Override
                public List<Member> deserialize(
                        JsonParser jsonParser, DeserializationContext deserializationContext
                ) throws IOException {
                    var addresses = new ArrayList<Member>();
                    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
                    for (var n : node) {
                        if (n.isTextual()) {
                            addresses.add(new Member(null, null, n.textValue()));
                        } else if (n.isNumber()) {
                            addresses.add(new Member(n.numberValue().longValue(), null, null));
                        } else {
                            var address = jsonParser.getCodec().treeToValue(n, JsonRecipientAddress.class);
                            addresses.add(new Member(null, address.uuid, address.number));
                        }
                    }

                    return addresses;
                }
            }
        }

        public record GroupV2(
                String groupId,
                String masterKey,
                String distributionId,
                @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean blocked,
                @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean permissionDenied
        ) {}
    }

    private static class GroupsDeserializer extends JsonDeserializer<List<Object>> {

        @Override
        public List<Object> deserialize(
                JsonParser jsonParser, DeserializationContext deserializationContext
        ) throws IOException {
            var groups = new ArrayList<>();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            for (var n : node) {
                Object g;
                if (n.hasNonNull("masterKey")) {
                    // a v2 group
                    g = jsonParser.getCodec().treeToValue(n, Storage.GroupV2.class);
                } else {
                    g = jsonParser.getCodec().treeToValue(n, Storage.GroupV1.class);
                }
                groups.add(g);
            }

            return groups;
        }
    }
}
