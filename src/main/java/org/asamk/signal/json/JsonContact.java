package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import io.micronaut.jsonschema.JsonSchema;

@JsonSchema(title = "Contact")
public record JsonContact(
        String number,
        String uuid,
        String username,
        String name,
        String givenName,
        String familyName,
        String nickName,
        String nickGivenName,
        String nickFamilyName,
        String note,
        String color,
        boolean isArchived,
        boolean isBlocked,
        boolean isHidden,
        int messageExpirationTime,
        boolean profileSharing,
        boolean unregistered,
        JsonProfile profile,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonInternal internal
) {

    @JsonSchema(title = "Profile")
    public record JsonProfile(
            long lastUpdateTimestamp,
            String givenName,
            String familyName,
            String about,
            String aboutEmoji,
            boolean hasAvatar,
            String mobileCoinAddress,
            List<JsonBadge> badges
    ) {}

    @JsonSchema(title = "Badge")
    public record JsonBadge(String id, String category, Long expirationTimestamp, boolean visible) {}

    @JsonSchema(title = "Internal")
    public record JsonInternal(
            List<String> capabilities,
            String unidentifiedAccessMode,
            Boolean sharesPhoneNumber,
            Boolean discoverableByPhonenumber
    ) {}
}
