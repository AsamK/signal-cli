package org.asamk.signal.manager.api;

import java.util.List;

public final class InvalidEnvelopeContentException extends Exception {

    public static final String INVALID_ENVELOPE_CONTENT = "INVALID_ENVELOPE_CONTENT";
    public static final String DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS = "DATA_MESSAGE_BODY_RANGE_OUT_OF_BOUNDS";

    private final String code;
    private final String sender;
    private final int senderDevice;
    private final Integer bodyLength;
    private final List<InvalidBodyRange> invalidBodyRanges;

    public InvalidEnvelopeContentException(
            final String message,
            final String code,
            final String sender,
            final int senderDevice,
            final Integer bodyLength,
            final List<InvalidBodyRange> invalidBodyRanges,
            final Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.sender = sender;
        this.senderDevice = senderDevice;
        this.bodyLength = bodyLength;
        this.invalidBodyRanges = List.copyOf(invalidBodyRanges);
    }

    public String getCode() {
        return code;
    }

    public String getSender() {
        return sender;
    }

    public int getSenderDevice() {
        return senderDevice;
    }

    public Integer getBodyLength() {
        return bodyLength;
    }

    public List<InvalidBodyRange> getInvalidBodyRanges() {
        return invalidBodyRanges;
    }

    public record InvalidBodyRange(int index, Integer start, Integer length, String type) {}
}
