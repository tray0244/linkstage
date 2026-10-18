package com.linkstage.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.linkstage.TestcontainersConfiguration;
import com.linkstage.payment.entity.Payment;
import com.linkstage.payment.entity.PaymentStatus;

// 시드의 1번 회차와 회원 1번으로 결제를 저장하고 다시 읽는다. 테스트가 넣은 행은 끝나면 지운다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentRepositoryTest {

	@Autowired
	PaymentRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	long holdGroupId;

	long otherHoldGroupId;

	@BeforeEach
	void insertHoldGroups() {
		holdGroupId = insertHoldGroup("req-payment-repo-1");
		otherHoldGroupId = insertHoldGroup("req-payment-repo-2");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM payment WHERE hold_group_id IN (?, ?)", holdGroupId, otherHoldGroupId);
		jdbc.update("DELETE FROM hold_group WHERE id IN (?, ?)", holdGroupId, otherHoldGroupId);
	}

	@Test
	void 준비한_결제를_저장하면_선점_번호로_다시_읽는다() {
		repository.saveAndFlush(Payment.ready("order-repo-1", 1L, holdGroupId, 150000));
		repository.saveAndFlush(Payment.ready("order-repo-2", 1L, otherHoldGroupId, 80000));

		Payment found = repository.findByHoldGroupId(holdGroupId).orElseThrow();

		assertThat(found.getOrderId()).isEqualTo("order-repo-1");
		assertThat(found.getHoldGroupId()).isEqualTo(holdGroupId);
		assertThat(found.getMemberId()).isEqualTo(1L);
		assertThat(found.getAmount()).isEqualTo(150000);
		assertThat(found.getStatus()).isEqualTo(PaymentStatus.READY);
		assertThat(found.getPaymentKey()).isNull();
		assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE hold_group_id = ?", String.class, holdGroupId))
			.isEqualTo("READY");
	}

	@Test
	void 결제가_없는_선점이면_다른_선점의_결제가_있어도_비어_있다() {
		repository.saveAndFlush(Payment.ready("order-repo-2", 1L, otherHoldGroupId, 80000));

		assertThat(repository.findByHoldGroupId(holdGroupId)).isEmpty();
	}

	private long insertHoldGroup(String requestId) {
		jdbc.update("INSERT INTO hold_group (request_id, member_id, schedule_id, status, requested_seat_count, expires_at)"
				+ " VALUES (?, 1, 1, 'HOLDING', 1, NOW(6))", requestId);
		return jdbc.queryForObject("SELECT id FROM hold_group WHERE request_id = ?", Long.class, requestId);
	}

}
