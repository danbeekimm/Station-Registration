package com.exem.dust.tcp.framing.dto;

/** 상대가 연결을 닫았다. */
public record EndOfStream() implements LineRead {
}
