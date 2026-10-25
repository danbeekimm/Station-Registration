package com.exem.dust.config.dto;

/** 기동 시 upsert하는 측정소({@code dust.stations[]}). 토큰은 해시해서 저장하고 평문은 메모리 밖으로 내보내지 않는다. */
public record StationSeed(String code, String name, String token) {

    /** 토큰이 로그에 남지 않도록 가린다. */
    @Override
    public String toString() {
        return "StationSeed[code=%s, name=%s, token=****]".formatted(code, name);
    }
}
