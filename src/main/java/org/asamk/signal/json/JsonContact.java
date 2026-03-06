package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;


import java.util.List;

@JsonSchema(title = "Contact")
public record JsonContact(
        @JsonProperty(required = true) String number,
        @JsonProperty(required = true) String uuid,
        @JsonProperty(required = true) String username,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) String givenName,
        @JsonProperty(required = true) String familyName,
        @JsonProperty(required = true) String nickName,
        @JsonProperty(required = true) String nickGivenName,
        @JsonProperty(required = true) String nickFamilyName,
        @JsonProperty(required = true) String note,
        @JsonProperty(required = true) String color,
        @JsonProperty(required = true) boolean isArchived,
        @JsonProperty(required = true) boolean isBlocked,
        @JsonProperty(required = true) boolean isHidden,
        @JsonProperty(required = true) int messageExpirationTime,
        @JsonProperty(required = true) boolean profileSharing,
        @JsonProperty(required = true) boolean unregistered,
        @JsonProperty(required = true) JsonProfile profile,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonInternal internal
) {

        @JsonSchema(title = "Profile")
    public record JsonProfile(
            @JsonProperty(required = true) long lastUpdateTimestamp,
            @JsonProperty(required = true) String givenName,
            @JsonProperty(required = true) String familyName,
            @JsonProperty(required = true) String about,
            @JsonProperty(required = true) String aboutEmoji,
            @JsonProperty(required = true) boolean hasAvatar,
            @JsonProperty(required = true) String mobileCoinAddress
    ) {}

        @JsonSchema(title = "Internal")
    public record JsonInternal(
            @JsonProperty(required = true) List<String> capabilities,
            @JsonProperty(required = true) String unidentifiedAccessMode,
            @JsonProperty(required = true) Boolean sharesPhoneNumber,
            @JsonProperty(required = true) Boolean discoverableByPhonenumber
    ) {}
}
