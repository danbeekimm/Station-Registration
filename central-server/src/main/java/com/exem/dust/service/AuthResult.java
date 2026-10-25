package com.exem.dust.service;

import com.exem.dust.common.protocol.RejectReason;
import com.exem.dust.domain.station.RegisteredStation;

/** HELLO 인증 결과. */
public sealed interface AuthResult {

    record Authenticated(RegisteredStation station) implements AuthResult {
    }

    record Rejected(RejectReason reason, String detail) implements AuthResult {
    }
}
