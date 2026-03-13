package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.jsonschema.JsonSchema;

import java.util.List;

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
            String mobileCoinAddress
    ) {}

    @JsonSchema(title = "Internal")
    public record JsonInternal(
            List<String> capabilities,
            String unidentifiedAccessMode,
            Boolean sharesPhoneNumber,
            Boolean discoverableByPhonenumber
    ) {}
}
