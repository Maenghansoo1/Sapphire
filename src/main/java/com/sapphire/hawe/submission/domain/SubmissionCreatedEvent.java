package com.sapphire.hawe.submission.domain;

public record SubmissionCreatedEvent(Long submissionId, String template, String aiGuess) { }
