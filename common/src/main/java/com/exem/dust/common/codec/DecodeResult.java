package com.exem.dust.common.codec;

import com.exem.dust.common.protocol.Message;
import com.exem.dust.common.protocol.RejectReason;

/** 한 줄 디코딩 결과. 실패도 예외가 아닌 값으로 돌려 호출자가 switch로 분기한다. */
public sealed interface DecodeResult {

    /** 형식이 올바른 메시지. */
    record Decoded(Message message) implements DecodeResult {
    }

    /** 거부 사유와 측정소에 돌려줄 짧은 설명. 내부 예외 문구는 담지 않는다. */
    record Invalid(RejectReason reason, String detail) implements DecodeResult {
    }
}
