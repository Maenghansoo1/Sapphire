package com.sapphire.hawe.reaction.dto;

import com.sapphire.hawe.submission.dto.ReactionSummary;
import java.util.List;

public record ReactionsResponse(List<ReactionSummary> reactions) { }
