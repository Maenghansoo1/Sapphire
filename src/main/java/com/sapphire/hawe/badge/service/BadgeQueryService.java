package com.sapphire.hawe.badge.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sapphire.hawe.badge.dao.BadgeMapper;
import com.sapphire.hawe.badge.dao.UserActivityMapper;
import com.sapphire.hawe.badge.domain.BadgeCatalog;
import com.sapphire.hawe.badge.domain.BadgeDefinition;
import com.sapphire.hawe.badge.domain.BadgeProgress;
import com.sapphire.hawe.badge.domain.BadgeStatus;
import com.sapphire.hawe.badge.domain.FeaturedBadgeSelector;
import com.sapphire.hawe.badge.domain.UserBadge;
import com.sapphire.hawe.badge.dto.BadgeCatalogItem;
import com.sapphire.hawe.badge.dto.FeaturedBadgeView;
import com.sapphire.hawe.badge.dto.MyBadgeResponse;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.time.SeoulTime;

@Service
public class BadgeQueryService {
	private final BadgeMapper badges;
	private final UserActivityMapper activity;

	public BadgeQueryService(BadgeMapper badges, UserActivityMapper activity) {
		this.badges = badges;
		this.activity = activity;
	}

	@Transactional(readOnly = true)
	public List<MyBadgeResponse> myBadges(Long userId) {
		List<UserBadge> owned = badges.findByUserId(userId);
		String featured = FeaturedBadgeSelector.select(badges.findFeaturedBadgeCode(userId), owned).orElse(null);
		return owned.stream().filter(b -> BadgeCatalog.byCode(b.badgeCode()).isPresent()).map(b -> {
			BadgeDefinition definition = BadgeCatalog.byCode(b.badgeCode()).orElseThrow();
			return new MyBadgeResponse(b.badgeCode(), definition.nameKo(), definition.descriptionKo(),
					definition.tier().name(), definition.imageKey(), SeoulTime.toOffset(b.earnedAt()),
					displayStatus(b.status()), b.status() == BadgeStatus.CONFIRMED ? b.tokenId() : null,
					null, b.badgeCode().equals(featured));
		}).toList();
	}

	@Transactional(readOnly = true)
	public List<BadgeCatalogItem> catalog(Long userId) {
		Map<String, UserBadge> owned = badges.findByUserId(userId).stream()
				.collect(Collectors.toMap(UserBadge::badgeCode, b -> b));
		var raw = BadgeProgress.raw(activity.findValidSubmissionsByUserId(userId),
				activity.findOnTimeSubmissionDays(userId), activity.findPerfectFitTimes(userId),
				activity.findCrowdMaxInteractions(userId), activity.findMemberGroupBases(userId), SeoulTime.today());
		return BadgeCatalog.all().stream().map(definition -> {
			UserBadge badge = owned.get(definition.code());
			boolean earned = badge != null;
			int progress = earned ? definition.target() : Math.max(0,
					Math.min(raw.getOrDefault(definition.code(), 0), definition.target() - 1));
			return new BadgeCatalogItem(definition.code(), definition.nameKo(), definition.descriptionKo(),
					definition.tier().name(), definition.imageKey(), definition.target(), progress, earned,
					earned ? SeoulTime.toOffset(badge.earnedAt()) : null);
		}).toList();
	}

	@Transactional
	public void setFeatured(Long userId, String code) {
		if (code != null && (BadgeCatalog.byCode(code).isEmpty()
				|| badges.findByUserIdAndCode(userId, code) == null))
			throw new BusinessException(BadgeErrorCode.BADGE_NOT_EARNED);
		badges.updateFeaturedBadge(userId, code);
	}

	@Transactional(readOnly = true)
	public Map<Long, FeaturedBadgeView> featuredBadges(Collection<Long> userIds) {
		if (userIds.isEmpty()) return Map.of();
		Map<Long, List<UserBadge>> owned = new HashMap<>();
		Map<Long, String> selected = new HashMap<>();
		for (var row : badges.findFeaturedBadgeRowsByUserIds(List.copyOf(userIds))) {
			selected.put(row.userId(), row.featuredBadgeCode());
			if (row.badgeCode() != null) owned.computeIfAbsent(row.userId(), ignored -> new ArrayList<>())
					.add(new UserBadge(null, row.userId(), row.badgeCode(), null,
							row.earnedAt(), null, null, null, null, null));
		}
		Map<Long, FeaturedBadgeView> result = new HashMap<>();
		for (var entry : owned.entrySet()) {
			FeaturedBadgeSelector.select(selected.get(entry.getKey()), entry.getValue()).ifPresent(code -> {
				BadgeDefinition definition = BadgeCatalog.byCode(code).orElseThrow();
				result.put(entry.getKey(), new FeaturedBadgeView(code, definition.nameKo(),
						definition.tier().name(), definition.imageKey()));
			});
		}
		return Map.copyOf(result);
	}

	private String displayStatus(BadgeStatus status) {
		return switch (status) {
			case ISSUED_OFFCHAIN, EARNED, MINTING -> "RECORDING";
			case CONFIRMED -> "ON_CHAIN";
			case FAILED -> "FAILED";
		};
	}
}
