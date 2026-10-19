package com.sapphire.hawe.dev.service;

import com.sapphire.hawe.dev.dto.DevDtos.ClockRequest;
import com.sapphire.hawe.dev.dto.DevDtos.ClockStatus;
import com.sapphire.hawe.global.exception.BusinessException;
import com.sapphire.hawe.global.exception.CommonErrorCode;
import com.sapphire.hawe.global.time.SeoulTime;
import com.sapphire.hawe.mission.service.MissionScheduler;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DevClockService {
    private static final Logger log = LoggerFactory.getLogger(DevClockService.class);
    private final MissionScheduler scheduler;
    public DevClockService(MissionScheduler scheduler) { this.scheduler = scheduler; }

    public ClockStatus status() {
        return new ClockStatus(SeoulTime.toOffset(SeoulTime.realNow()), SeoulTime.toOffset(SeoulTime.now()),
                Math.round(SeoulTime.offset().toMillis() / 1000.0), !SeoulTime.offset().isZero(), null);
    }

    public ClockStatus set(ClockRequest request) {
        if (request == null || (request.now() == null) == (request.plusSeconds() == null))
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        LocalDateTime before = SeoulTime.now();
        LocalDateTime target;
        try {
            target = request.now() != null ? parse(request.now()) : before.plusSeconds(request.plusSeconds());
            SeoulTime.setOffset(request.now() != null ? Duration.between(SeoulTime.realNow(), target)
                    : SeoulTime.offset().plusSeconds(request.plusSeconds()));
        } catch (RuntimeException error) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        if (!Boolean.FALSE.equals(request.publish())) {
            try { scheduler.publishDueMissions(); }
            catch (RuntimeException error) { log.warn("Developer clock publish failed", error); }
        }
        ClockStatus result = status();
        return new ClockStatus(result.realNow(), result.serverNow(), result.offsetSeconds(), result.offsetActive(),
                target.isBefore(before) ? "과거로 되돌리면 이미 발행된 미션/가입 시각과 어긋날 수 있음" : null);
    }

    public ClockStatus reset() { SeoulTime.reset(); return status(); }

    private LocalDateTime parse(String value) {
        try { return OffsetDateTime.parse(value).atZoneSameInstant(SeoulTime.ZONE).toLocalDateTime(); }
        catch (RuntimeException ignored) { return LocalDateTime.parse(value); }
    }
}
