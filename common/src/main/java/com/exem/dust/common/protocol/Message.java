package com.exem.dust.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 측정소와 중앙 서버가 NDJSON 한 줄로 주고받는 메시지. {@code type} 필드로 종류를 구분한다.
 * <p>흐름: HELLO → WELCOME | REJECT, 이후 DATA → ACK | REJECT 반복, 종료 시 BYE.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Hello.class, name = "HELLO"),
        @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
        @JsonSubTypes.Type(value = Reject.class, name = "REJECT"),
        @JsonSubTypes.Type(value = Data.class, name = "DATA"),
        @JsonSubTypes.Type(value = Ack.class, name = "ACK"),
        @JsonSubTypes.Type(value = Bye.class, name = "BYE")
})
public sealed interface Message permits Hello, Welcome, Reject, Data, Ack, Bye {
}
