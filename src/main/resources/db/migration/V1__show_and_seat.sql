-- 공연·회차·좌석 정보와, 그 테이블들이 가리키는 회원·운영자.
-- 기준: docs/ERD-v1.0.md. 상태 컬럼은 ENUM 대신 VARCHAR(30)을 쓴다 (값은 자바 enum이 지킨다).

CREATE TABLE member (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255) NOT NULL,
    -- 탈퇴하지 않은 계정끼리만 이메일이 겹치지 않게 한다. 탈퇴 계정은 NULL이 되어 UNIQUE에서 빠진다.
    active_email_uk VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN email END) STORED,
    password_hash   VARCHAR(100) NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    phone_number    VARCHAR(20)  NULL,
    token_version   INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_active_email (active_email_uk)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE admin (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- SHOW는 MySQL 예약어라서 백틱으로 감싼다.
CREATE TABLE `show` (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    created_by_id         BIGINT       NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    description           TEXT         NULL,
    venue_name            VARCHAR(100) NOT NULL,
    open_at               DATETIME(6)  NOT NULL,
    max_tickets_per_order INT          NOT NULL DEFAULT 4,
    status                VARCHAR(30)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at            DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_show_admin FOREIGN KEY (created_by_id) REFERENCES admin (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE schedule (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    show_id    BIGINT      NOT NULL,
    starts_at  DATETIME(6) NOT NULL,
    status     VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_show FOREIGN KEY (show_id) REFERENCES `show` (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE seat_grade (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    show_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    price      INT         NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_seat_grade_show FOREIGN KEY (show_id) REFERENCES `show` (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE seat (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    show_id       BIGINT      NOT NULL,
    seat_grade_id BIGINT      NOT NULL,
    section       VARCHAR(20) NOT NULL,
    row_label     VARCHAR(10) NOT NULL,
    seat_no       INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_seat_show FOREIGN KEY (show_id) REFERENCES `show` (id) ON DELETE CASCADE,
    CONSTRAINT fk_seat_seat_grade FOREIGN KEY (seat_grade_id) REFERENCES seat_grade (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 회차별 좌석. status는 AVAILABLE / RESERVED만 쓴다. HELD는 Redis에만 있다.
CREATE TABLE schedule_seat (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT      NOT NULL,
    seat_id     BIGINT      NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    version     BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- 회차당 좌석은 1행
    UNIQUE KEY uk_schedule_seat (schedule_id, seat_id),
    CONSTRAINT fk_schedule_seat_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_seat_seat FOREIGN KEY (seat_id) REFERENCES seat (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
