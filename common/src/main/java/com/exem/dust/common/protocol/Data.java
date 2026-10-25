package com.exem.dust.common.protocol;

import java.time.LocalDateTime;

/**
 * 측정값 1건. 농도가 null이면 해당 장비가 점검 중이라는 뜻이다.
 *
 * @param seq        측정소별 1부터 증가하는 전송 순번
 * @param measuredAt 측정 시각(정시). 경보 지속 판정은 이 논리 시간으로 한다
 * @param o3         오존 ppm. 제공 데이터에는 없어 항상 null이지만 오존 주의보 룰의 입력 자리로 둔다
 */
public record Data(String stationId, int seq, LocalDateTime measuredAt, Double pm10, Double pm25, Double o3)
        implements Message {
}
