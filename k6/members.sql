-- k6 부하 테스트용 회원 1~500번. k6의 VU 번호(1~500)를 X-Member-Id로 쓴다.
-- 이미 있는 회원(시드의 1·2번)은 INSERT IGNORE로 건너뛰므로 여러 번 실행해도 결과가 같다.
-- mysql 클라이언트 기본 문자 집합이 latin1이면 한글 이름이 깨지므로 먼저 맞춘다.
SET NAMES utf8mb4;

INSERT IGNORE INTO member (id, email, password_hash, name)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 500)
SELECT i, CONCAT('k6-user', i, '@linkstage.local'), 'not-used', CONCAT('k6회원', i)
FROM n;
