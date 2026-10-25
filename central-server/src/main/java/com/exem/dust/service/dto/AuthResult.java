package com.exem.dust.service.dto;

/** HELLO 인증 결과. */
public sealed interface AuthResult permits StationAuthenticated, AuthRejected {
}
