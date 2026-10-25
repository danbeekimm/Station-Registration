package com.exem.dust.tcp;

/** 한 줄 읽기 결과. 길이 초과를 별도 값으로 돌려 호출자가 MALFORMED로 거부하고 접속은 유지할 수 있게 한다. */
sealed interface LineRead {

    /** 온전한 한 줄(개행 제외). */
    record Line(String text) implements LineRead {
    }

    /** 상한을 넘어 버린 줄. 내용은 보존하지 않는다. */
    record TooLong() implements LineRead {
    }

    /** 상대가 연결을 닫았다. */
    record EndOfStream() implements LineRead {
    }
}
