package com.sapphire.hawe.mascot.service;

import com.sapphire.hawe.comment.dao.CommentInsertRow;
import com.sapphire.hawe.comment.dao.CommentMapper;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.mascot.dao.MascotMapper;
import com.sapphire.hawe.reaction.dao.ReactionMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class MascotService {
    private static final Logger log = LoggerFactory.getLogger(MascotService.class);
    private static final List<String> STICKERS = List.of("heart", "laugh", "wow", "fire");
    private final MascotMapper mascot;
    private final CommentMapper comments;
    private final ReactionMapper reactions;
    private final ObjectMapper json;
    private final IntSupplier random;
    private volatile Optional<Long> cachedUserId;

    @Autowired
    public MascotService(MascotMapper mascot, CommentMapper comments, ReactionMapper reactions, ObjectMapper json) {
        this(mascot, comments, reactions, json, () -> ThreadLocalRandom.current().nextInt(STICKERS.size()));
    }

    MascotService(MascotMapper mascot, CommentMapper comments, ReactionMapper reactions, ObjectMapper json, IntSupplier random) {
        this.mascot = mascot;
        this.comments = comments;
        this.reactions = reactions;
        this.json = json;
        this.random = random;
    }

    public Optional<Long> userId() {
        Optional<Long> result = cachedUserId;
        if (result != null) return result;
        synchronized (this) {
            if (cachedUserId == null) {
                cachedUserId = Optional.ofNullable(mascot.findUserId());
                if (cachedUserId.isEmpty()) log.warn("Mascot user is missing; AI comments and reactions are disabled");
            }
            return cachedUserId;
        }
    }

    public void reactRandomly(Long submissionId) {
        userId().ifPresent(id -> {
            if (reactions.existsBySubmissionIdAndUserId(submissionId, id)) return;
            String sticker = STICKERS.get(Math.floorMod(random.getAsInt(), STICKERS.size()));
            try { reactions.insert(submissionId, id, sticker); }
            catch (DuplicateKeyException ignored) { }
        });
    }

    public void commentPhotoScore(Long submissionId, String text, int score) {
        String detail = json.writeValueAsString(Map.of("kind", "PHOTO_SCORE", "score", Math.clamp(score, 0, 100)));
        insertComment(submissionId, text, detail);
    }

    public void commentDrawGuess(Long submissionId, String rawGuessJson) {
        List<Guess> guesses = parseGuesses(rawGuessJson);
        if (guesses.isEmpty()) return;
        String detail = json.writeValueAsString(Map.of("kind", "DRAW_GUESS", "guesses",
                guesses.stream().map(guess -> Map.of("label", guess.label(), "probability", guess.probability())).toList()));
        insertComment(submissionId, guesses.getFirst().label() + "인가요?", detail);
    }

    private void insertComment(Long submissionId, String text, String detail) {
        userId().ifPresent(id -> {
            if (comments.existsBySubmissionIdAndUserId(submissionId, id)) return;
            comments.insert(new CommentInsertRow(submissionId, id, text, null, detail, SeoulTime.now().withNano(0)));
        });
    }

    private List<Guess> parseGuesses(String raw) {
        if (raw == null || raw.length() > 1000) return List.of();
        try {
            Object value = json.readValue(raw, Object.class);
            if (!(value instanceof List<?> items)) return List.of();
            List<Guess> valid = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> map) || !(map.get("label") instanceof String label)
                        || !(map.get("probability") instanceof Number number)) continue;
                label = label.strip();
                int length = label.codePointCount(0, label.length());
                double probability = number.doubleValue();
                if (length < 1 || length > 20 || label.codePoints().anyMatch(Character::isISOControl)
                        || !Double.isFinite(probability) || probability < 0 || probability > 1) continue;
                valid.add(new Guess(label, Math.round(probability * 10000) / 10000.0));
            }
            return valid.stream().sorted(Comparator.comparingDouble(Guess::probability).reversed()).limit(3).toList();
        } catch (Exception ignored) { return List.of(); }
    }

    private record Guess(String label, double probability) { }
}
