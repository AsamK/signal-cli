package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public record TextStyle(Style style, int start, int length) {

    public enum Style {
        NONE,
        BOLD,
        ITALIC,
        SPOILER,
        STRIKETHROUGH,
        MONOSPACE;

        static Style fromInternal(SignalServiceProtos.BodyRange.Style style) {
            return switch (style) {
                case NONE -> NONE;
                case BOLD -> BOLD;
                case ITALIC -> ITALIC;
                case SPOILER -> SPOILER;
                case STRIKETHROUGH -> STRIKETHROUGH;
                case MONOSPACE -> MONOSPACE;
            };
        }

        public static Style from(String style) {
            return switch (style) {
                case "NONE" -> NONE;
                case "BOLD" -> BOLD;
                case "ITALIC" -> ITALIC;
                case "SPOILER" -> SPOILER;
                case "STRIKETHROUGH" -> STRIKETHROUGH;
                case "MONOSPACE" -> MONOSPACE;
                default -> null;
            };
        }

        SignalServiceProtos.BodyRange.Style toBodyRangeStyle() {
            return switch (this) {
                case NONE -> SignalServiceProtos.BodyRange.Style.NONE;
                case BOLD -> SignalServiceProtos.BodyRange.Style.BOLD;
                case ITALIC -> SignalServiceProtos.BodyRange.Style.ITALIC;
                case SPOILER -> SignalServiceProtos.BodyRange.Style.SPOILER;
                case STRIKETHROUGH -> SignalServiceProtos.BodyRange.Style.STRIKETHROUGH;
                case MONOSPACE -> SignalServiceProtos.BodyRange.Style.MONOSPACE;
            };
        }
    }

    static TextStyle from(SignalServiceProtos.BodyRange bodyRange) {
        return new TextStyle(Style.fromInternal(bodyRange.getStyle()), bodyRange.getStart(), bodyRange.getLength());
    }

    public SignalServiceProtos.BodyRange toBodyRange() {
        return SignalServiceProtos.BodyRange.newBuilder()
                .setStart(this.start())
                .setLength(this.length())
                .setStyle(this.style().toBodyRangeStyle())
                .build();
    }
}
