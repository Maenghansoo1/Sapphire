package com.sapphire.hawe.character.domain;

public class ProfileCharacter {

    private Long id;
    private String name;
    private String svgKey;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSvgKey() {
        return svgKey;
    }

    public void setSvgKey(String svgKey) {
        this.svgKey = svgKey;
    }
}
