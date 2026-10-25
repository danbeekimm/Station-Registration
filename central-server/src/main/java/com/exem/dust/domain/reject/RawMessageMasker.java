package com.exem.dust.domain.reject;

import java.util.regex.Pattern;

/** 거부 원문을 저장하기 전에 토큰 값을 가린다. 형식이 깨진 HELLO라도 토큰 문자열이 그대로 남지 않게 한다. */
public final class RawMessageMasker {

    private static final Pattern TOKEN_FIELD = Pattern.compile("(\"token\"\\s*:\\s*\")[^\"]*(\")");

    private RawMessageMasker() {
    }

    /** "token":"…" 값을 ****로 바꾼다. 패턴이 없으면 그대로 돌려준다. */
    public static String maskToken(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }
        return TOKEN_FIELD.matcher(rawMessage).replaceAll("$1****$2");
    }
}
