package com.sapphire.hawe.poke.dao;

public class PokedRow {
    private Long toUserId;
    private String type;

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
