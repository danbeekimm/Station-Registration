package com.exem.dust.service.ingest.dto;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * DATA 검증에 필요한, 세션이 알고 있는 직전 상태.
 *
 * @param lastSeenSeq    마지막으로 받아들인(또는 거부로 소비한) seq. 다음 건은 +1, 같은 값은 재전송으로 허용
 * @param lastMeasuredAt 저장된 마지막 측정 시각. 같으면 재전송(중복), 이전이면 시간 역행
 */
public record ValidationContext(String stationId, int lastSeenSeq, Optional<LocalDateTime> lastMeasuredAt) {
}
