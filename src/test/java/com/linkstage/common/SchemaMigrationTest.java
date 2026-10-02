package com.linkstage.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.linkstage.TestcontainersConfiguration;

// Testcontainers가 띄운 실제 MySQL에 Flyway 마이그레이션이 적용됐는지 확인한다.
// @Transactional이라 테스트가 넣은 행은 끝나면 되돌려진다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaMigrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void 마이그레이션이_끝나면_테이블_9개가_있다() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);

		assertThat(tables).contains("member", "admin", "show", "schedule", "seat_grade", "seat", "schedule_seat",
				"hold_group", "seat_hold");
	}

	@Test
	void 같은_회차와_좌석을_두_번_넣으면_거부한다() {
		long scheduleId = insertSchedule();
		long showId = jdbc.queryForObject("SELECT show_id FROM schedule WHERE id = ?", Long.class, scheduleId);
		jdbc.update("INSERT INTO seat_grade (show_id, name, price) VALUES (?, 'R', 100000)", showId);
		long gradeId = lastInsertId();
		jdbc.update("INSERT INTO seat (show_id, seat_grade_id, section, row_label, seat_no) VALUES (?, ?, 'A', '1', 1)",
				showId, gradeId);
		long seatId = lastInsertId();

		jdbc.update("INSERT INTO schedule_seat (schedule_id, seat_id) VALUES (?, ?)", scheduleId, seatId);

		assertThatThrownBy(
				() -> jdbc.update("INSERT INTO schedule_seat (schedule_id, seat_id) VALUES (?, ?)", scheduleId, seatId))
			.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void 같은_request_id의_선점_그룹을_두_번_넣으면_거부한다() {
		long scheduleId = insertSchedule();
		jdbc.update("INSERT INTO member (email, password_hash, name) VALUES ('m@test.local', 'x', '회원')");
		long memberId = lastInsertId();
		String sql = "INSERT INTO hold_group (request_id, member_id, schedule_id, status, requested_seat_count, expires_at)"
				+ " VALUES ('req-1', ?, ?, 'HOLDING', 1, NOW(6))";

		jdbc.update(sql, memberId, scheduleId);

		assertThatThrownBy(() -> jdbc.update(sql, memberId, scheduleId)).isInstanceOf(DuplicateKeyException.class);
	}

	private long insertSchedule() {
		jdbc.update("INSERT INTO admin (email, password_hash, name) VALUES ('a@test.local', 'x', '운영자')");
		long adminId = lastInsertId();
		jdbc.update("INSERT INTO `show` (created_by_id, title, venue_name, open_at, status)"
				+ " VALUES (?, '테스트 공연', '테스트 홀', NOW(6), 'ON_SALE')", adminId);
		long showId = lastInsertId();
		jdbc.update("INSERT INTO schedule (show_id, starts_at, status) VALUES (?, NOW(6), 'ON_SALE')", showId);
		return lastInsertId();
	}

	// 같은 트랜잭션(같은 연결) 안에서 방금 넣은 행의 id를 읽는다.
	private long lastInsertId() {
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

}
