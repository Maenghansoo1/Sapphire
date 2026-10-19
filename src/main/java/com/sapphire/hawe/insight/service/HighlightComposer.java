package com.sapphire.hawe.insight.service;

import com.sapphire.hawe.insight.dto.GroupInsightResponse;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class HighlightComposer {
    public List<String> compose(YearMonth month, GroupInsightResponse.ActivityIndex activity,
            GroupInsightResponse.Fastest fastest, List<com.sapphire.hawe.insight.domain.InsightPayload.Word> words,
            GroupInsightResponse.MostReacted most) {
        List<String> lines = new ArrayList<>();
        int number = month.getMonthValue();
        if (fastest != null && fastest.nickname() != null)
            lines.add(number + "월의 빠른 손은 " + fastest.nickname() + "님! 가장 먼저 낸 횟수 " + fastest.firstCount() + "번");
        if (!words.isEmpty()) {
            var word = words.getFirst();
            lines.add(number + "월에 가장 자주 나온 단어는 '" + word.word() + "'(" + word.count() + "번)");
        }
        if (most != null && most.missionContent() != null) {
            String content = most.missionContent();
            int length = content.codePointCount(0, content.length());
            if (length > 20) content = content.substring(0, content.offsetByCodePoints(0, 20)) + "…";
            lines.add("가장 뜨거웠던 글은 ‘" + content + "’에 달린 "
                    + (most.reactionCount() + most.commentCount()) + "개의 반응");
        }
        if (activity != null) lines.add("우리 그룹 활동 지수 " + activity.score() + "점");
        return List.copyOf(lines);
    }
}
