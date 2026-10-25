package com.exem.dust.common.protocol;

/** 인증 성공 응답. 측정소는 {@code resumeFromSeq}부터 DATA를 보낸다. */
public record Welcome(int resumeFromSeq) implements Message {
}
