package com.exem.dust.service;

import com.exem.dust.config.DustProperties;
import com.exem.dust.config.dto.StationSeed;
import com.exem.dust.dao.StationDao;
import com.exem.dust.domain.station.RegisteredStation;
import com.exem.dust.domain.station.TokenHasher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * application.yml의 측정소 시드를 기동 시 한 번 upsert한다. 평문 토큰은 여기서 해시되어 DB에는 해시만 남는다.
 * 기동 이후의 측정소 추가는 registerStation 태스크가 같은 DAO로 처리하므로 서버 재기동이 필요 없다.
 */
@Component
@DependsOn("schemaInitializer")
public class StationSeeder {

    private static final Logger log = LoggerFactory.getLogger(StationSeeder.class);

    private final DustProperties properties;
    private final StationDao stationDao;

    public StationSeeder(DustProperties properties, StationDao stationDao) {
        this.properties = properties;
        this.stationDao = stationDao;
    }

    /** 시드 항목을 검증하고 upsert한다. 값이 비어 있으면 잘못된 설정이므로 기동을 중단한다. */
    @PostConstruct
    public void seed() {
        for (var seed : properties.stations()) {
            requireText(seed.code(), "code", seed);
            requireText(seed.name(), "name", seed);
            requireText(seed.token(), "token", seed);
            stationDao.upsert(new RegisteredStation(seed.code(), seed.name(), TokenHasher.hash(seed.code(), seed.token())));
        }
        log.info("측정소 시드 {}건 반영: {}", properties.stations().size(),
                properties.stations().stream().map(seed -> seed.code() + " " + seed.name()).toList());
    }

    private static void requireText(String value, String field, StationSeed seed) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("dust.stations 항목에 %s가 비어 있습니다: %s".formatted(field, seed));
        }
    }
}
