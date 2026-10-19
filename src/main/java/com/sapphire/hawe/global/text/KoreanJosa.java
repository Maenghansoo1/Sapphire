package com.sapphire.hawe.global.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 받침에 맞는 조사를 고른다. 앱(Android `core/KoreanText.kt`)과 같은 규칙으로, 한글로 끝나지 않으면 받침이 없다고 본다. */
public final class KoreanJosa {

    public static final String TARGET = "[대상]";

    // [대상] 바로 뒤에 붙은 조사. 긴 것(이랑)을 먼저 보고, 뒤에 한글이 이어지면(이다, 가방) 조사가 아니라서 건드리지 않는다.
    private static final Pattern AFTER_TARGET =
            Pattern.compile(Pattern.quote(TARGET) + "(이랑|랑|으로|로|을|를|이|가|은|는|과|와)(?![가-힣])");

    private KoreanJosa() {
    }

    public static boolean hasFinalConsonant(String word) {
        return finalConsonant(word) > 0;
    }

    /** 구름을, 고양이를 */
    public static String object(String word) {
        return word + (hasFinalConsonant(word) ? "을" : "를");
    }

    /** 풍선으로, 집으로 / 별자리로, 퇴근길로(ㄹ 받침은 "로") */
    public static String toward(String word) {
        return word + (towardNeedsEu(word) ? "으로" : "로");
    }

    /** 문장의 [대상]을 닉네임으로 바꾸고, 바로 뒤 조사를 닉네임 받침에 맞춘다("[대상]을" → "찬희를", "[대상]와" → "우준과"). */
    public static String replaceTarget(String content, String nickname) {
        if (content == null || !content.contains(TARGET)) {
            return content;
        }
        Matcher matcher = AFTER_TARGET.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(nickname + particle(nickname, matcher.group(1))));
        }
        matcher.appendTail(out);
        return out.toString().replace(TARGET, nickname);
    }

    private static String particle(String word, String written) {
        boolean consonant = hasFinalConsonant(word);
        return switch (written) {
            case "이랑", "랑" -> consonant ? "이랑" : "랑";
            case "으로", "로" -> towardNeedsEu(word) ? "으로" : "로";
            case "을", "를" -> consonant ? "을" : "를";
            case "이", "가" -> consonant ? "이" : "가";
            case "은", "는" -> consonant ? "은" : "는";
            default -> consonant ? "과" : "와";
        };
    }

    private static boolean towardNeedsEu(String word) {
        int last = finalConsonant(word);
        return last > 0 && last != RIEUL;
    }

    private static final int RIEUL = 8;

    /** 마지막 글자의 받침 번호(0 = 없음). 한글 음절이 아니면 0. */
    private static int finalConsonant(String word) {
        if (word == null || word.isEmpty()) {
            return 0;
        }
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' ? (last - '가') % 28 : 0;
    }
}