package com.exem.dust.common.codec.dto;

/** 한 줄 디코딩 결과. 실패도 예외가 아닌 값으로 돌려 호출자가 switch로 분기한다. */
public sealed interface DecodeResult permits DecodedMessage, InvalidMessage {
}
