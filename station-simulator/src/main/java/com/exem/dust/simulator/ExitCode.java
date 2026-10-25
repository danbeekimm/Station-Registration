package com.exem.dust.simulator;

/** 프로세스 종료 코드. run-stations.sh와 README가 이 값으로 결과를 설명한다. */
enum ExitCode {
    SUCCESS(0),
    FAILURE(1),
    AUTH_REJECTED(2);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    int code() {
        return code;
    }
}
