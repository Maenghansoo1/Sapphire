package com.sapphire.hawe.reaction.domain;

public enum StickerType {
    HEART("heart"), LAUGH("laugh"), WOW("wow"), SAD("sad"), FIRE("fire");

    private final String value;
    StickerType(String value) { this.value = value; }
    public String value() { return value; }
    public static StickerType fromValue(String value) {
        for (StickerType type : values()) if (type.value.equals(value)) return type;
        return null;
    }
}
