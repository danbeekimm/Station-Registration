package com.exem.dust.config.dto;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * TCP 수신 설정({@code dust.tcp.*}).
 *
 * @param helloTimeout 접속 후 HELLO가 와야 하는 시간. 빈 연결 점유 방지
 * @param readTimeout  인증 뒤 메시지 간 최대 공백. 1초 간격 전송이 이만큼 멈추면 비정상으로 본다
 * @param maxLineBytes 한 줄(메시지 1건)의 최대 바이트
 */
public record TcpProperties(
        @DefaultValue("9000") int port,
        @DefaultValue("5s") Duration helloTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("4096") int maxLineBytes) {
}
