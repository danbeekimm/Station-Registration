package com.exem.dust.common.protocol;

/** DATA 저장 완료 응답. 같은 seq가 다시 와도(재전송) 서버는 ACK를 다시 보낸다. */
public record Ack(int seq) implements Message {
}
