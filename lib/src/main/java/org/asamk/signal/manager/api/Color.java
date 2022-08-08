package org.asamk.signal.manager.api;

public record Color(int color) {

    public int alpha() {
        return color >>> 24;
    }

    public int red() {
        return (color >> 16) & 0xFF;
    }

    public int green() {
        return (color >> 8) & 0xFF;
    }

    public int blue() {
        return color & 0xFF;
    }

    public String toHexColor() {
        return String.format("#%08x", color);
    }
}
