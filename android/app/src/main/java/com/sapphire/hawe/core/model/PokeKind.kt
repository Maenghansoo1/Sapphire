package com.sapphire.hawe.core.model

/** 재촉 종류(콕 찌르기·똥뿌리기). `wire` 는 서버 `type` 값. */
enum class PokeKind(val wire: String) {
    POKE("poke"), FLING("fling");
    companion object { fun fromWire(value: String): PokeKind? = entries.firstOrNull { it.wire == value } }
}
