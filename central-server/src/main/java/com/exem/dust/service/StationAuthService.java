package com.exem.dust.service;

import com.exem.dust.common.protocol.Hello;
import com.exem.dust.common.protocol.Protocol;
import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.dao.StationDao;
import com.exem.dust.domain.station.TokenHasher;
import org.springframework.stereotype.Service;

/**
 * HELLO의 사전 공유 토큰을 station 테이블의 해시와 비교한다.
 * 접속당 한 번만 조회하므로 캐시를 두지 않고, 그 덕에 registerStation 직후 바로 접속이 된다.
 */
@Service
public class StationAuthService {

    private final StationDao stationDao;

    public StationAuthService(StationDao stationDao) {
        this.stationDao = stationDao;
    }

    /** 프로토콜 버전 → 필수 필드 → 등록 여부 → 토큰 순으로 검사한다. */
    public AuthResult authenticate(Hello hello) {
        if (hello.protocol() != Protocol.VERSION) {
            return new AuthResult.Rejected(RejectReason.UNSUPPORTED_PROTOCOL, "지원 프로토콜 버전은 " + Protocol.VERSION);
        }
        if (isBlank(hello.stationId()) || isBlank(hello.token())) {
            return new AuthResult.Rejected(RejectReason.AUTH_FAILED, "stationId와 token은 필수");
        }
        var registered = stationDao.findById(hello.stationId());
        if (registered.isEmpty()) {
            return new AuthResult.Rejected(RejectReason.UNKNOWN_STATION, "등록되지 않은 측정소: " + hello.stationId());
        }
        var station = registered.get();
        if (!TokenHasher.matches(station.tokenHash(), station.id(), hello.token())) {
            return new AuthResult.Rejected(RejectReason.AUTH_FAILED, "토큰 불일치");
        }
        return new AuthResult.Authenticated(station);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
