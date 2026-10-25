package com.exem.dust.common.protocol;

/** 인증 또는 검증 실패 응답. 소켓을 닫을지는 {@link RejectReason#closesConnection()}이 정한다. */
public record Reject(RejectReason reason, String detail) implements Message {
}
