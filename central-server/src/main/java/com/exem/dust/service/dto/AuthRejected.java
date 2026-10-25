package com.exem.dust.service.dto;

import com.exem.dust.common.protocol.RejectReason;

/** 거부 사유와 측정소에 돌려줄 설명. 모두 소켓을 닫는 사유다. */
public record AuthRejected(RejectReason reason, String detail) implements AuthResult {
}
