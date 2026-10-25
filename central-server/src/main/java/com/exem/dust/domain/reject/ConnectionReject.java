package com.exem.dust.domain.reject;

import com.exem.dust.common.protocol.RejectReason;

import java.time.Instant;

/**
 * 인증·검증 거부 1건. connection_reject_log 한 행과 대응하며, 검증이 실제로 동작했다는 근거로 남는다.
 *
 * @param stationId  인증 전 거부면 HELLO가 주장한 값, 인증 후면 인증된 측정소. 알 수 없으면 null
 * @param rawMessage 거부된 원문의 앞부분. 토큰이 들어 있을 수 있는 HELLO는 호출자가 가린 문자열을 넘긴다
 */
public record ConnectionReject(Instant rejectedAt, String remoteIp, String stationId, RejectReason reason, String rawMessage) {

    public static final int RAW_MESSAGE_LIMIT = 512;

    /** 현재 시각으로 거부 기록을 만든다. 원문은 저장 한도에 맞춰 자른다. */
    public static ConnectionReject now(String remoteIp, String stationId, RejectReason reason, String rawMessage) {
        return new ConnectionReject(Instant.now(), remoteIp, stationId, reason, truncate(rawMessage));
    }

    /** 원문 앞부분만 남긴다. 컬럼 길이(512)를 넘는 입력도 기록은 남아야 한다. */
    private static String truncate(String rawMessage) {
        if (rawMessage == null || rawMessage.length() <= RAW_MESSAGE_LIMIT) {
            return rawMessage;
        }
        return rawMessage.substring(0, RAW_MESSAGE_LIMIT);
    }
}
