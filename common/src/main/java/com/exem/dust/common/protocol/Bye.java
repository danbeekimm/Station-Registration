package com.exem.dust.common.protocol;

/** 측정소의 정상 종료 통지. */
public record Bye(String stationId) implements Message {
}
