package com.linkstage.show.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkstage.show.dto.ShowResponse;
import com.linkstage.show.service.ShowQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "공연", description = "판매 중인 공연과 회차")
@RestController
@RequiredArgsConstructor
public class ShowController {

	private final ShowQueryService showQueryService;

	@Operation(summary = "공연 목록", description = "판매 중(ON_SALE)인 공연과 그 회차 목록을 준다. 성공하면 200.")
	@GetMapping("/api/shows")
	public List<ShowResponse> shows() {
		return showQueryService.findOnSaleShows();
	}

}
