-- 초기 측정소 3곳. 중앙 서버가 기동할 때마다 schema.sql 다음에 실행하며, 이미 있는 측정소는 건드리지 않는다(ON CONFLICT DO NOTHING).
--   psql -U dust -h localhost -d dust -f db/seed.sql
-- 빈 DB를 처음 채우는 용도다. 이후 측정소 추가·토큰 변경은 registerStation(DB)으로만 하며, 재기동이 그 변경을 되돌리지 않는다.
-- 토큰은 평문으로 저장하지 않고 서버의 TokenHasher와 같은 규칙 SHA-256('측정소코드:토큰')의 소문자 16진수로 넣는다.
-- 아래 토큰은 개발·평가용이며 run-stations.sh / .env.example의 기본값과 같다.

INSERT INTO station (station_id, name, token_hash) VALUES
    ('111311', '노원구', encode(sha256(convert_to('111311:dev-token-nowon-111311', 'UTF8')), 'hex')),
    ('111121', '중구',   encode(sha256(convert_to('111121:dev-token-junggu-111121', 'UTF8')), 'hex')),
    ('111212', '강서구', encode(sha256(convert_to('111212:dev-token-gangseo-111212', 'UTF8')), 'hex'))
ON CONFLICT (station_id) DO NOTHING;
