package com.exem.dust.service;

import com.exem.dust.common.protocol.Data;
import com.exem.dust.common.protocol.Reject;
import com.exem.dust.common.protocol.RejectReason;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 인증된 세션에서 받은 DATA의 의미 검증. 형식(JSON) 검증은 코덱이 이미 끝낸 상태다. */
@Component
public class MessageValidator {

    /**
     * @param authenticatedStationId HELLO로 인증된 측정소. DATA의 stationId가 이와 다르면 소유권 위반이다
     */
    public Optional<Reject> validate(Data data, String authenticatedStationId) {
        if (data.stationId() == null || data.measuredAt() == null || data.seq() <= 0) {
            return Optional.of(new Reject(RejectReason.SCHEMA, "stationId, seq(1 이상), measuredAt는 필수"));
        }
        if (!data.stationId().equals(authenticatedStationId)) {
            return Optional.of(new Reject(RejectReason.FORBIDDEN, "인증된 측정소와 다른 stationId"));
        }
        return Optional.empty();
    }
}
