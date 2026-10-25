package com.exem.dust.common.protocol;

/** 거부 사유 코드. 치명적인 사유(인증·소유권)는 소켓을 닫고, 메시지 단위 검증 실패는 접속을 유지한다. */
public enum RejectReason {
    HELLO_EXPECTED(true),
    HELLO_TIMEOUT(true),
    UNSUPPORTED_PROTOCOL(true),
    UNKNOWN_STATION(true),
    AUTH_FAILED(true),
    FORBIDDEN(true),
    MALFORMED(false),
    SCHEMA(false),
    UNEXPECTED_MESSAGE(false),
    OUT_OF_ORDER(false),
    BAD_TIME(false),
    OUT_OF_RANGE(false);

    private final boolean closesConnection;

    RejectReason(boolean closesConnection) {
        this.closesConnection = closesConnection;
    }

    /** 이 사유로 거부하면 서버가 소켓을 닫는다. 측정소는 재접속으로 대응한다. */
    public boolean closesConnection() {
        return closesConnection;
    }

    /** 측정소 등록이나 토큰 문제라 재시도해도 소용없는 거부. 시뮬레이터가 종료 코드로 구분해 안내한다. */
    public boolean isAuthFailure() {
        return this == UNKNOWN_STATION || this == AUTH_FAILED;
    }
}
