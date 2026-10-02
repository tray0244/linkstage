package com.linkstage.show;

// DB에 남는 좌석 상태. HELD는 Redis에만 있어서 여기에 없다.
public enum ScheduleSeatStatus {

	AVAILABLE, RESERVED

}
