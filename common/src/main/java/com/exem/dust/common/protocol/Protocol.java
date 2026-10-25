package com.exem.dust.common.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** 양쪽이 공유하는 프로토콜 상수. */
public final class Protocol {

    public static final int VERSION = 1;
    public static final Charset CHARSET = StandardCharsets.UTF_8;
    /** 한 줄(메시지 1건)의 최대 바이트. 넘는 입력은 읽지 않고 버려 메모리 소모를 막는다. */
    public static final int MAX_LINE_BYTES = 4096;

    private Protocol() {
    }
}
