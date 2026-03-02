package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Contact")
public record JsonContact(
        @Schema(required = true) String number,
        @Schema(required = true) String uuid,
        @Schema(required = true) String username,
        @Schema(required = true) String name,
        @Schema(required = true) String givenName,
        @Schema(required = true) String familyName,
        @Schema(required = true) String nickName,
        @Schema(required = true) String nickGivenName,
        @Schema(required = true) String nickFamilyName,
        @Schema(required = true) String note,
        @Schema(required = true) String color,
        @Schema(required = true) boolean isBlocked,
        @Schema(required = true) boolean isHidden,
        @Schema(required = true) int messageExpirationTime,
        @Schema(required = true) boolean profileSharing,
        @Schema(required = true) boolean unregistered,
        @Schema(required = true) JsonProfile profile,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonInternal internal
) {

    @Schema(name = "Profile")
    public record JsonProfile(
            @Schema(required = true) long lastUpdateTimestamp,
            @Schema(required = true) String givenName,
            @Schema(required = true) String familyName,
            @Schema(required = true) String about,
            @Schema(required = true) String aboutEmoji,
            @Schema(required = true) boolean hasAvatar,
            @Schema(required = true) String mobileCoinAddress
    ) {}

    @Schema(name = "Internal")
    public record JsonInternal(
            @Schema(required = true) List<String> capabilities,
            @Schema(required = true) String unidentifiedAccessMode,
            @Schema(required = true) Boolean sharesPhoneNumber,
            @Schema(required = true) Boolean discoverableByPhonenumber
    ) {}
}
