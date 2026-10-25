package com.exem.dust.tcp.dto;

/** 상한을 넘어 버린 줄. 내용은 보존하지 않는다. */
public record LineTooLong() implements LineRead {
}
