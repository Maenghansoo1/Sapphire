package com.sapphire.hawe.badge.service;

import java.util.Map;

public record SweepResult(int evaluated, int failed, Map<String, Integer> awardedByCode) {
}
