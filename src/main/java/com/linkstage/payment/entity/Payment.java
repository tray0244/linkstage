package com.linkstage.payment.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 하나. 좌석 선점(hold_group) 하나에 결제 하나다.
// 회원과 선점은 다른 패키지의 엔티티라서 id만 둔다.
@Entity
@Table(name = "payment")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false, length = 64)
	private String orderId;

	// 토스가 승인할 때 준다. 승인 전에는 null이다.
	@Column(name = "payment_key", length = 200)
	private String paymentKey;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "hold_group_id", nullable = false)
	private Long holdGroupId;

	// 원 단위
	@Column(nullable = false)
	private int amount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private PaymentStatus status;

	@Column(name = "fail_reason")
	private String failReason;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public static Payment ready(String orderId, Long memberId, Long holdGroupId, int amount) {
		Payment payment = new Payment();
		payment.orderId = orderId;
		payment.memberId = memberId;
		payment.holdGroupId = holdGroupId;
		payment.amount = amount;
		payment.status = PaymentStatus.READY;
		return payment;
	}

}
