package com.exem.dust.config;

import com.exem.dust.config.dto.SlackProperties;
import com.exem.dust.config.dto.TcpProperties;
import com.exem.dust.config.dto.ValidationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml의 {@code dust.*} 설정. 환경변수와 실행 인자로 덮어쓸 수 있다. 하위 설정은 config.dto의 record들이다.
 * 측정소 목록은 설정이 아니라 데이터라 여기 없다. 초기 등록은 db/seed.sql, 이후 변경은 registerStation(DB)이 맡는다.
 */
@ConfigurationProperties(prefix = "dust")
public record DustProperties(
        TcpProperties tcp,
        @DefaultValue ValidationProperties validation,
        @DefaultValue SlackProperties slack) {
}
