package com.exem.dust.common.data;

/** 데이터 파일을 쓸 수 없는 상태(빈 파일, JSON 아님, 측정소 선택 불가). 복구할 수 없어 호출자는 종료한다. */
public class DustDataException extends RuntimeException {

    public DustDataException(String message) {
        super(message);
    }

    public DustDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
