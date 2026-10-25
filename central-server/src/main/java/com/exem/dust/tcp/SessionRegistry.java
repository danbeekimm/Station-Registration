package com.exem.dust.tcp;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증된 활성 접속. 측정소 하나에 접속 하나만 유지하며, 같은 측정소의 새 접속이 인증을 통과하면 기존 접속을 닫는다
 * (최신 접속 우선). 측정소가 BYE 없이 죽어도 읽기 타임아웃을 기다리지 않고 바로 재접속할 수 있다.
 */
@Component
public class SessionRegistry {

    private final Map<String, ConnectionHandler> sessions = new ConcurrentHashMap<>();

    /** 등록하고, 교체된 기존 세션이 있으면 돌려준다. 닫는 책임은 호출자에게 있다. */
    ConnectionHandler register(String stationId, ConnectionHandler handler) {
        return sessions.put(stationId, handler);
    }

    /** 자기 자신이 아직 등록되어 있을 때만 제거한다. 교체된 세션이 뒤늦게 종료되며 새 세션을 지우는 일을 막는다. */
    void unregister(String stationId, ConnectionHandler handler) {
        sessions.remove(stationId, handler);
    }

    public int activeCount() {
        return sessions.size();
    }

    void closeAll() {
        sessions.values().forEach(ConnectionHandler::closeQuietly);
        sessions.clear();
    }
}
