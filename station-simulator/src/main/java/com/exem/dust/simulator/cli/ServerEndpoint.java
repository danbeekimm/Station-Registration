package com.exem.dust.simulator.cli;

/** 중앙 서버 주소. "host:port" 문자열에서 만든다. */
public record ServerEndpoint(String host, int port) {

    /** "host:port"를 해석한다. 콜론이 여럿이면(IPv6) 마지막 콜론을 포트 구분자로 본다. */
    public static ServerEndpoint parse(String value) {
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("서버 주소는 host:port 형식이어야 합니다: " + value);
        }
        int port;
        try {
            port = Integer.parseInt(value.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("포트가 숫자가 아닙니다: " + value);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("포트 범위(1~65535)를 벗어났습니다: " + port);
        }
        return new ServerEndpoint(value.substring(0, separator), port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
