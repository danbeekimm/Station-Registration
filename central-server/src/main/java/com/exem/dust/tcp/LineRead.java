package com.exem.dust.tcp;

/** 한 줄 읽기 결과. 길이 초과를 별도 값으로 돌려 호출자가 MALFORMED로 거부하고 접속은 유지할 수 있게 한다. */
sealed interface LineRead {

    record Line(String text) implements LineRead {
    }

    record TooLong() implements LineRead {
    }

    record EndOfStream() implements LineRead {
    }
}
