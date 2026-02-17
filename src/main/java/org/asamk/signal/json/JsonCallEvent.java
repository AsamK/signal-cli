package org.asamk.signal.json;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.asamk.signal.manager.api.CallInfo;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public record JsonCallEvent(
        long callId,
        String state,
        @JsonInclude(NON_NULL) String number,
        @JsonInclude(NON_NULL) String uuid,
        boolean isOutgoing,
        @JsonInclude(NON_NULL) String inputDeviceName,
        @JsonInclude(NON_NULL) String outputDeviceName,
        @JsonInclude(NON_NULL) String reason
) {

    public static JsonCallEvent from(CallInfo callInfo, String reason) {
        return new JsonCallEvent(
                callInfo.callId(),
                callInfo.state().name(),
                callInfo.recipient().number().orElse(null),
                callInfo.recipient().aci().orElse(null),
                callInfo.isOutgoing(),
                callInfo.inputDeviceName(),
                callInfo.outputDeviceName(),
                reason
        );
    }
}
