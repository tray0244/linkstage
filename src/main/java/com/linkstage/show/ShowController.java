package com.linkstage.show;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ShowController {

	private final ShowQueryService showQueryService;

	@GetMapping("/api/shows")
	public List<ShowResponse> shows() {
		return showQueryService.findOnSaleShows();
	}

}
