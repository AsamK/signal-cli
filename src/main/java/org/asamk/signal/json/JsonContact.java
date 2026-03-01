package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Contact")
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
        boolean isBlocked,
        boolean isHidden,
        int messageExpirationTime,
        boolean profileSharing,
        boolean unregistered,
        JsonProfile profile,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonInternal internal
) {

    @Schema(name = "Profile")
    public record JsonProfile(
            long lastUpdateTimestamp,
            String givenName,
            String familyName,
            String about,
            String aboutEmoji,
            boolean hasAvatar,
            String mobileCoinAddress
    ) {}

    @Schema(name = "Internal")
    public record JsonInternal(
            List<String> capabilities,
            String unidentifiedAccessMode,
            Boolean sharesPhoneNumber,
            Boolean discoverableByPhonenumber
    ) {}
}
