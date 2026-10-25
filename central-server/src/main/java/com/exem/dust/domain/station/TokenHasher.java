package com.exem.dust.domain.station;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 측정소 토큰 해시. 측정소 ID를 함께 섭취해 같은 토큰이라도 측정소마다 해시가 달라지고,
 * 한 측정소의 해시가 다른 측정소 인증에 쓰일 수 없게 한다.
 */
public final class TokenHasher {

    private static final HexFormat HEX = HexFormat.of();

    private TokenHasher() {
    }

    public static String hash(String stationId, String token) {
        var digest = sha256();
        digest.update(stationId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(token.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(digest.digest());
    }

    /** 길이와 내용이 달라도 비교 시간이 같도록 상수 시간 비교를 쓴다. */
    public static boolean matches(String expectedHash, String stationId, String presentedToken) {
        var presented = hash(stationId, presentedToken).getBytes(StandardCharsets.US_ASCII);
        var expected = expectedHash.strip().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, presented);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM", e);
        }
    }
}
