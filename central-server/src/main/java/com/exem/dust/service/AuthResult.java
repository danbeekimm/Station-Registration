package com.exem.dust.service;

import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.domain.station.RegisteredStation;

/** HELLO 인증 결과. */
public sealed interface AuthResult {

    /** 토큰 일치. 세션은 이 측정소로 고정된다. */
    record Authenticated(RegisteredStation station) implements AuthResult {
    }

    /** 거부 사유와 측정소에 돌려줄 설명. 모두 소켓을 닫는 사유다. */
    record Rejected(RejectReason reason, String detail) implements AuthResult {
    }
}
