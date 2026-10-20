package com.linkstage.seat.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 좌석 선점 요청 하나. 선점 판정은 Redis Lua가 하고, 이 테이블에는 기록을 남긴다.
// 회원과 회차는 다른 패키지의 엔티티라서 id만 둔다.
@Entity
@Table(name = "hold_group")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false, length = 64)
	private String requestId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "schedule_id", nullable = false)
	private Long scheduleId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private HoldGroupStatus status;

	@Column(name = "requested_seat_count", nullable = false)
	private int requestedSeatCount;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@OneToMany(mappedBy = "holdGroup", cascade = CascadeType.PERSIST)
	private List<SeatHold> seatHolds = new ArrayList<>();

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public static HoldGroup hold(String requestId, Long memberId, Long scheduleId, List<Long> scheduleSeatIds,
			LocalDateTime expiresAt) {
		HoldGroup group = new HoldGroup();
		group.requestId = requestId;
		group.memberId = memberId;
		group.scheduleId = scheduleId;
		group.status = HoldGroupStatus.HOLDING;
		group.requestedSeatCount = scheduleSeatIds.size();
		group.expiresAt = expiresAt;
		for (Long scheduleSeatId : scheduleSeatIds) {
			group.seatHolds.add(new SeatHold(group, scheduleSeatId));
		}
		return group;
	}

	public List<Long> scheduleSeatIds() {
		return seatHolds.stream().map(SeatHold::getScheduleSeatId).toList();
	}

}
