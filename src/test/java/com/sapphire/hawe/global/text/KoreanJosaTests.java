package com.sapphire.hawe.global.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoreanJosaTests {

    @Test void objectAndToward() {
        assertEquals("구름을", KoreanJosa.object("구름"));
        assertEquals("고양이를", KoreanJosa.object("고양이"));
        assertEquals("풍선으로", KoreanJosa.toward("풍선"));
        assertEquals("집으로", KoreanJosa.toward("집"));
        assertEquals("퇴근길로", KoreanJosa.toward("퇴근길"));
        assertEquals("별자리로", KoreanJosa.toward("별자리"));
        assertEquals("cat로", KoreanJosa.toward("cat"));
        assertTrue(KoreanJosa.hasFinalConsonant("우준"));
        assertFalse(KoreanJosa.hasFinalConsonant("찬희"));
        assertFalse(KoreanJosa.hasFinalConsonant(""));
    }

    @Test void replaceTargetFixesParticleAfterNickname() {
        assertEquals("찬희를 풍선으로 바꿔 그려줘", KoreanJosa.replaceTarget("[대상]을 풍선으로 바꿔 그려줘", "찬희"));
        assertEquals("우준을 풍선으로 바꿔 그려줘", KoreanJosa.replaceTarget("[대상]를 풍선으로 바꿔 그려줘", "우준"));
        assertEquals("우준과 같이 가고 싶은 곳은 ___이야", KoreanJosa.replaceTarget("[대상]와 같이 가고 싶은 곳은 ___이야", "우준"));
        assertEquals("찬희가 좋아하는 것", KoreanJosa.replaceTarget("[대상]이 좋아하는 것", "찬희"));
        assertEquals("우준은, 우준은", KoreanJosa.replaceTarget("[대상]는, [대상]은", "우준"));
        assertEquals("한별로 바꿔", KoreanJosa.replaceTarget("[대상]으로 바꿔", "한별"));
        assertEquals("우준이랑 같이", KoreanJosa.replaceTarget("[대상]랑 같이", "우준"));
        // 조사가 아닌 것(의, 에게, 뒤에 한글이 이어짐)은 그대로
        assertEquals("찬희의 하루", KoreanJosa.replaceTarget("[대상]의 하루", "찬희"));
        assertEquals("찬희에게", KoreanJosa.replaceTarget("[대상]에게", "찬희"));
        assertEquals("찬희이다", KoreanJosa.replaceTarget("[대상]이다", "찬희"));
        assertEquals("문장", KoreanJosa.replaceTarget("문장", "찬희"));
        assertNull(KoreanJosa.replaceTarget(null, "찬희"));
        assertEquals("$1를", KoreanJosa.replaceTarget("[대상]을", "$1"));
    }
}