package com.sapphire.hawe.badge.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sapphire.hawe.badge.dto.BadgeCatalogItem;
import com.sapphire.hawe.badge.dto.FeaturedBadgeRequest;
import com.sapphire.hawe.badge.dto.MyBadgeResponse;
import com.sapphire.hawe.badge.service.BadgeQueryService;

@RestController
@RequestMapping("/api")
public class BadgeController {
	private final BadgeQueryService badges;

	public BadgeController(BadgeQueryService badges) {
		this.badges = badges;
	}

	@GetMapping("/me/badges")
	public List<MyBadgeResponse> myBadges(@AuthenticationPrincipal Long userId) {
		return badges.myBadges(userId);
	}

	@GetMapping("/badges/catalog")
	public List<BadgeCatalogItem> catalog(@AuthenticationPrincipal Long userId) {
		return badges.catalog(userId);
	}

	@PutMapping("/me/badges/featured")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setFeatured(@AuthenticationPrincipal Long userId, @RequestBody FeaturedBadgeRequest request) {
		badges.setFeatured(userId, request.code());
	}
}
