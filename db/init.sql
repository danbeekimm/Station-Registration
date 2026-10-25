-- 앱 접속용 롤과 데이터베이스를 만든다. 관리자(superuser)로 postgres DB에 접속해 실행한다.
--   psql -U postgres -h localhost -f db/init.sql
-- 두 번 실행해도 안전하다. 롤과 DB가 이미 있으면 아무것도 하지 않는다.
-- docker compose는 이 파일을 /docker-entrypoint-initdb.d/ 에 마운트해 최초 기동 시 1회 실행한다.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'dust') THEN
        CREATE ROLE dust LOGIN PASSWORD 'dust';
    END IF;
END
$$;

-- CREATE DATABASE는 트랜잭션 안에서 실행할 수 없고 IF NOT EXISTS도 없어서 psql의 \gexec로 조건 실행한다.
SELECT 'CREATE DATABASE dust OWNER dust'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'dust')\gexec
