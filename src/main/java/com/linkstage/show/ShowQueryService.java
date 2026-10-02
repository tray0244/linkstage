package com.linkstage.show;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowQueryService {

	private final ShowRepository showRepository;

	// 판매 중인 공연과 그 회차 목록
	public List<ShowResponse> findOnSaleShows() {
		return showRepository.findAllWithSchedules(ShowStatus.ON_SALE).stream().map(ShowResponse::from).toList();
	}

}
