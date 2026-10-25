package com.exem.dust.domain.event;

import com.exem.dust.common.protocol.RejectReason;

/** 접속 또는 메시지가 거부됐다. 트랜잭션 밖에서 발행되며 지표 집계가 구독한다. */
public record ConnectionRejectedEvent(String stationId, RejectReason reason) {
}
