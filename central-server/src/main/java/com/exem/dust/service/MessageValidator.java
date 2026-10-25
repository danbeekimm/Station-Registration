package com.exem.dust.service;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.config.DustProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 인증된 세션에서 받은 DATA의 의미 검증. 형식(JSON) 검증은 코덱이 이미 끝낸 상태다.
 * 검사 순서: 필수 필드 → 소유권 → 순서 → 시각 → 범위. 거부된 메시지는 저장·판정·통보 어느 단계도 거치지 않는다.
 */
@Component
public class MessageValidator {

    /**
     * 세션이 알고 있는 직전 상태.
     *
     * @param lastSeenSeq    마지막으로 받아들인(또는 거부로 소비한) seq. 다음 건은 +1, 같은 값은 재전송으로 허용
     * @param lastMeasuredAt 저장된 마지막 측정 시각. 같으면 재전송(중복), 이전이면 시간 역행
     */
    public record SessionContext(String stationId, int lastSeenSeq, Optional<LocalDateTime> lastMeasuredAt) {
    }

    private final DustProperties.Validation limits;
    private final Clock clock;

    public MessageValidator(DustProperties properties, Clock clock) {
        this.limits = properties.validation();
        this.clock = clock;
    }

    public Optional<Reject> validate(Data data, SessionContext context) {
        if (data.stationId() == null || data.measuredAt() == null || data.seq() <= 0) {
            return reject(RejectReason.SCHEMA, "stationId, seq(1 이상), measuredAt는 필수");
        }
        if (!data.stationId().equals(context.stationId())) {
            return reject(RejectReason.FORBIDDEN, "인증된 측정소와 다른 stationId");
        }
        boolean isNext = data.seq() == context.lastSeenSeq() + 1;
        boolean isResend = data.seq() == context.lastSeenSeq();
        if (!isNext && !isResend) {
            return reject(RejectReason.OUT_OF_ORDER, "기대 seq %d, 수신 seq %d".formatted(context.lastSeenSeq() + 1, data.seq()));
        }
        return validateTime(data, context).or(() -> validateRange(data));
    }

    private Optional<Reject> validateTime(Data data, SessionContext context) {
        var measuredAt = data.measuredAt();
        if (measuredAt.getMinute() != 0 || measuredAt.getSecond() != 0 || measuredAt.getNano() != 0) {
            return reject(RejectReason.BAD_TIME, "측정 시각은 정시여야 함: " + measuredAt);
        }
        if (context.lastMeasuredAt().filter(measuredAt::isBefore).isPresent()) {
            return reject(RejectReason.BAD_TIME, "마지막 측정 시각 %s보다 이전: %s".formatted(context.lastMeasuredAt().get(), measuredAt));
        }
        var horizon = LocalDateTime.now(clock).plus(limits.maxFutureSkew());
        if (measuredAt.isAfter(horizon)) {
            return reject(RejectReason.BAD_TIME, "미래 시각: " + measuredAt);
        }
        return Optional.empty();
    }

    private Optional<Reject> validateRange(Data data) {
        if (outOfRange(data.pm10(), limits.maxDustConcentration())) {
            return reject(RejectReason.OUT_OF_RANGE, "pm10 허용 범위 0~%.0f: %s".formatted(limits.maxDustConcentration(), data.pm10()));
        }
        if (outOfRange(data.pm25(), limits.maxDustConcentration())) {
            return reject(RejectReason.OUT_OF_RANGE, "pm25 허용 범위 0~%.0f: %s".formatted(limits.maxDustConcentration(), data.pm25()));
        }
        if (outOfRange(data.o3(), limits.maxOzonePpm())) {
            return reject(RejectReason.OUT_OF_RANGE, "o3 허용 범위 0~%.1f: %s".formatted(limits.maxOzonePpm(), data.o3()));
        }
        return Optional.empty();
    }

    /** null은 결측(점검)이라 범위 검사 대상이 아니다. NaN·무한대는 비교에서 걸러진다. */
    private static boolean outOfRange(Double value, double max) {
        return value != null && !(value >= 0 && value <= max);
    }

    private static Optional<Reject> reject(RejectReason reason, String detail) {
        return Optional.of(new Reject(reason, detail));
    }
}
