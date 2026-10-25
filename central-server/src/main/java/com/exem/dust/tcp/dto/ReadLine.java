package com.exem.dust.tcp.dto;

/** 온전한 한 줄(개행 제외). */
public record ReadLine(String text) implements LineRead {
}
