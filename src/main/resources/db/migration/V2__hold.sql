-- 좌석 선점 기록. 실제 선점 판정은 Redis Lua가 하고, 여기에는 이력을 남긴다.
-- 이력 테이블이라 FK는 RESTRICT(기본값)다. 기준: docs/ERD-v1.0.md.

CREATE TABLE hold_group (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    -- 선점 요청 멱등키. Redis의 hold_request 키와 같은 값이다.
    request_id           VARCHAR(64) NOT NULL,
    member_id            BIGINT      NOT NULL,
    schedule_id          BIGINT      NOT NULL,
    status               VARCHAR(30) NOT NULL,
    requested_seat_count INT         NOT NULL,
    expires_at           DATETIME(6) NOT NULL,
    created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at           DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hold_group_request_id (request_id),
    CONSTRAINT fk_hold_group_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_hold_group_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE seat_hold (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    hold_group_id    BIGINT      NOT NULL,
    schedule_seat_id BIGINT      NOT NULL,
    status           VARCHAR(30) NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at       DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_seat_hold_hold_group FOREIGN KEY (hold_group_id) REFERENCES hold_group (id),
    CONSTRAINT fk_seat_hold_schedule_seat FOREIGN KEY (schedule_seat_id) REFERENCES schedule_seat (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
