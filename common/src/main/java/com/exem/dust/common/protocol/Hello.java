package com.exem.dust.common.protocol;

/**
 * 접속 직후 측정소가 한 번 보내는 인증 요청.
 *
 * @param lastAckedSeq 측정소가 마지막으로 ACK를 받은 seq. 재접속 시 서버가 재개 지점을 계산하는 근거
 */
public record Hello(String stationId, String stationName, String token, int protocol, int lastAckedSeq)
        implements Message {

    /** 토큰이 로그에 남지 않도록 가린다. */
    @Override
    public String toString() {
        return "Hello[stationId=%s, stationName=%s, token=****, protocol=%d, lastAckedSeq=%d]"
                .formatted(stationId, stationName, protocol, lastAckedSeq);
    }
}
