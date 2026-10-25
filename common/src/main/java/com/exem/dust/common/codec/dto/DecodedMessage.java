package com.exem.dust.common.codec.dto;

import com.exem.dust.common.protocol.Message;

/** 형식이 올바른 메시지. */
public record DecodedMessage(Message message) implements DecodeResult {
}
