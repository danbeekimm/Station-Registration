package com.exem.dust.domain.station;

/** station 테이블 한 행. 측정소 목록과 인증 해시의 단일 출처다. */
public record RegisteredStation(String id, String name, String tokenHash) {
}
