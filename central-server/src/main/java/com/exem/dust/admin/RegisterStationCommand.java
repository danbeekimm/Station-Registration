package com.exem.dust.admin;

import com.exem.dust.domain.station.TokenHasher;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 운영 중 측정소 등록 명령. 서버를 거치지 않고 station 테이블에 직접 upsert하므로 서버 재기동이 필요 없다.
 * 서버는 HELLO마다 DB를 읽기 때문에 등록 직후 바로 접속이 된다.
 * <pre>
 *   ./gradlew registerStation --code=111191 --name=은평구 --token=dev-token-eunpyeong-111191
 * </pre>
 * DB 접속 정보는 서버와 같은 환경변수(DUST_DB_URL, DUST_DB_USER, DUST_DB_PASSWORD)를 쓴다.
 */
public final class RegisterStationCommand {

    private static final String UPSERT = """
            INSERT INTO station (station_id, name, token_hash)
            VALUES (?, ?, ?)
            ON CONFLICT (station_id) DO UPDATE
                SET name = EXCLUDED.name, token_hash = EXCLUDED.token_hash, updated_at = now()
            """;

    private RegisterStationCommand() {
    }

    /** 인자를 해석해 토큰을 해시한 뒤 station에 upsert한다. 실패하면 종료 코드 1. */
    public static void main(String[] args) {
        var options = parse(args);
        var code = require(options, "code");
        var name = require(options, "name");
        var token = require(options, "token");

        var url = envOrDefault("DUST_DB_URL", "jdbc:postgresql://localhost:5432/dust");
        var user = envOrDefault("DUST_DB_USER", "dust");
        var password = envOrDefault("DUST_DB_PASSWORD", "dust");

        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setString(3, TokenHasher.hash(code, token));
            statement.executeUpdate();
            System.out.printf("측정소 등록 완료: %s %s (%s)%n", code, name, url);
        } catch (SQLException e) {
            System.err.println("측정소 등록 실패: " + e.getMessage());
            System.exit(1);
        }
    }

    /** --key=value 인자를 맵으로 바꾼다. 다른 형식은 사용법을 보여 주고 끝낸다. */
    private static Map<String, String> parse(String[] args) {
        var options = new HashMap<String, String>();
        for (var argument : args) {
            int equalsAt = argument.indexOf('=');
            if (!argument.startsWith("--") || equalsAt < 3) {
                fail("인자는 --key=value 형식이어야 합니다: " + argument);
            }
            options.put(argument.substring(2, equalsAt), argument.substring(equalsAt + 1));
        }
        return options;
    }

    /** 필수 옵션이 비어 있으면 사용법과 함께 끝낸다. */
    private static String require(Map<String, String> options, String key) {
        var value = options.get(key);
        if (value == null || value.isBlank()) {
            fail("--" + key + " 값이 필요합니다");
        }
        return value;
    }

    /** 서버와 같은 환경변수 이름을 쓴다. 비어 있으면 compose 기본값. */
    private static String envOrDefault(String name, String defaultValue) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 오류와 사용법을 표준 오류에 쓰고 종료 코드 1로 끝낸다. */
    private static void fail(String message) {
        System.err.println(message);
        System.err.println("사용법: ./gradlew registerStation --code=<측정소코드> --name=<이름> --token=<토큰>");
        System.exit(1);
    }
}
