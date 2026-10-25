package com.exem.dust.common.codec.dto;

import com.exem.dust.common.protocol.RejectReason;

/** 거부 사유와 측정소에 돌려줄 짧은 설명. 내부 예외 문구는 담지 않는다. */
public record InvalidMessage(RejectReason reason, String detail) implements DecodeResult {
}
