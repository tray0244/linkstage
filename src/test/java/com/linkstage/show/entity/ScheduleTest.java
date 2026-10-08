package com.linkstage.show.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

// Spring을 띄우지 않고 엔티티만 확인한다.
// Schedule에는 상태를 넣는 생성자·setter가 없어서, 테스트에서만 ReflectionTestUtils로 필드에 넣는다.
class ScheduleTest {

	@ParameterizedTest
	@EnumSource(ScheduleStatus.class)
	void 판매_중은_ON_SALE일_때만이다(ScheduleStatus status) {
		Schedule schedule = new Schedule();
		ReflectionTestUtils.setField(schedule, "status", status);

		assertThat(schedule.isOnSale()).isEqualTo(status == ScheduleStatus.ON_SALE);
	}

}
