-- local 프로필 전용 시드. 내용이 바뀌면 Flyway가 다시 실행하므로 여러 번 실행해도 결과가 같게 쓴다
-- (고정 id + INSERT IGNORE).

INSERT IGNORE INTO admin (id, email, password_hash, name)
VALUES (1, 'admin@linkstage.local', 'not-used', '운영자');

INSERT IGNORE INTO member (id, email, password_hash, name)
VALUES (1, 'user1@linkstage.local', 'not-used', '테스트회원1'),
       (2, 'user2@linkstage.local', 'not-used', '테스트회원2');

INSERT IGNORE INTO `show` (id, created_by_id, title, description, venue_name, open_at, max_tickets_per_order, status)
VALUES (1, 1, '링크스테이지 콘서트', '시드 공연', '링크홀', '2026-10-01 20:00:00', 4, 'ON_SALE');

INSERT IGNORE INTO schedule (id, show_id, starts_at, status)
VALUES (1, 1, '2026-12-24 19:00:00', 'ON_SALE'),
       (2, 1, '2026-12-25 19:00:00', 'ON_SALE');

INSERT IGNORE INTO seat_grade (id, show_id, name, price)
VALUES (1, 1, 'R', 110000),
       (2, 1, 'S', 88000);

-- 좌석 50개: A~E열 × 1~10번. A·B열은 R석, C~E열은 S석.
INSERT IGNORE INTO seat (id, show_id, seat_grade_id, section, row_label, seat_no)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 50)
SELECT i, 1, IF(i <= 20, 1, 2), '1층', CHAR(65 + FLOOR((i - 1) / 10) USING utf8mb4), ((i - 1) % 10) + 1
FROM n;

-- 회차 2개 × 좌석 50개 = 100행. UNIQUE(schedule_id, seat_id)가 중복을 막는다.
INSERT IGNORE INTO schedule_seat (schedule_id, seat_id)
SELECT sc.id, s.id
FROM schedule sc
         JOIN seat s ON s.show_id = sc.show_id
WHERE sc.show_id = 1;
