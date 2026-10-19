package com.sapphire.hawe.submission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiReviewResponse(String badge, String comment) { }
