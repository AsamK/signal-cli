package org.asamk.signal.manager.api;

import org.whispersystems.signalservice.internal.push.BodyRange;

public record TextStyle(Style style, Integer start, Integer length) {

    public enum Style {
        NONE,
        BOLD,
        ITALIC,
        SPOILER,
        STRIKETHROUGH,
        MONOSPACE;

        static Style fromInternal(BodyRange.Style style) {
            if (style == null) {
                return NONE;
            }
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

        BodyRange.Style toBodyRangeStyle() {
            return switch (this) {
                case NONE -> BodyRange.Style.NONE;
                case BOLD -> BodyRange.Style.BOLD;
                case ITALIC -> BodyRange.Style.ITALIC;
                case SPOILER -> BodyRange.Style.SPOILER;
                case STRIKETHROUGH -> BodyRange.Style.STRIKETHROUGH;
                case MONOSPACE -> BodyRange.Style.MONOSPACE;
            };
        }
    }

    static TextStyle from(BodyRange bodyRange) {
        return new TextStyle(Style.fromInternal(bodyRange.style), bodyRange.start, bodyRange.length);
    }

    public BodyRange toBodyRange() {
        return new BodyRange.Builder().start(this.start())
                .length(this.length())
                .style(this.style().toBodyRangeStyle())
                .build();
    }
}
