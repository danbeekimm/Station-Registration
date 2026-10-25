package com.exem.dust.web.controller;

import com.exem.dust.web.sse.SseBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** GET /stream — measurement · alert · maintenance 세 종류의 SSE 이벤트를 보낸다. */
@RestController
public class EventStreamController {

    private final SseBroadcaster broadcaster;

    public EventStreamController(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /** 구독자를 등록하고 열린 응답을 돌려준다. 타임아웃이 없어 브라우저가 떠날 때까지 유지된다. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
