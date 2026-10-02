package com.linkstage.show;

// API가 돌려주는 좌석 상태. DB 상태(AVAILABLE/RESERVED)와 Redis 상태(HELD/RESERVED)를 합친 값이다.
public enum SeatStatus {

	AVAILABLE, HELD, RESERVED

}
