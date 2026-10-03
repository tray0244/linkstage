package com.linkstage.show.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// SHOW는 MySQL 예약어라서 테이블 이름을 백틱으로 감싼다.
@Entity
@Table(name = "`show`")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Show {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "created_by_id", nullable = false)
	private Long createdById;

	@Column(nullable = false, length = 200)
	private String title;

	@JdbcTypeCode(SqlTypes.LONGVARCHAR)
	private String description;

	@Column(name = "venue_name", nullable = false, length = 100)
	private String venueName;

	@Column(name = "open_at", nullable = false)
	private LocalDateTime openAt;

	@Column(name = "max_tickets_per_order", nullable = false)
	private int maxTicketsPerOrder;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ShowStatus status;

	@OneToMany(mappedBy = "show")
	@OrderBy("startsAt asc")
	private List<Schedule> schedules = new ArrayList<>();

	// created_at, updated_at은 DB 기본값이 채운다.
	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

}
