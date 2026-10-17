-- 결제. 좌석 선점(hold_group) 하나에 결제 하나다. 기준: docs/ERD-v1.0.md.
-- 취소 관련 컬럼(cancel_fee_amount, refund_amount, fee_tier_id, canceled_at)은 예매 취소를 만들 때 더한다.

CREATE TABLE payment (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    -- 서버가 만든 주문 번호(UUID). 토스 규칙: 영문·숫자·-·_ 6~64자.
    order_id      VARCHAR(64)  NOT NULL,
    -- 토스가 승인할 때 주는 결제 키. 승인 전에는 NULL이다. 토스 문서: 최대 200자.
    payment_key   VARCHAR(200) NULL,
    member_id     BIGINT       NOT NULL,
    hold_group_id BIGINT       NOT NULL,
    -- 원 단위. 잡은 좌석들의 등급 가격 합계.
    amount        INT          NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    fail_reason   VARCHAR(255) NULL,
    approved_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_id (order_id),
    -- NULL은 UNIQUE에 걸리지 않는다. 승인 전 결제가 여러 개여도 된다.
    UNIQUE KEY uk_payment_payment_key (payment_key),
    -- 선점 하나에 결제 하나. 카드 실패 뒤 다시 결제할 때 막히면 "진행 중인 결제만 UNIQUE"로 바꾼다.
    UNIQUE KEY uk_payment_hold_group_id (hold_group_id),
    CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_payment_hold_group FOREIGN KEY (hold_group_id) REFERENCES hold_group (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
