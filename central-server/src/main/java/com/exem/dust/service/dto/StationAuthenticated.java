package com.exem.dust.service.dto;

import com.exem.dust.domain.station.RegisteredStation;

/** 토큰 일치. 세션은 이 측정소로 고정된다. */
public record StationAuthenticated(RegisteredStation station) implements AuthResult {
}
