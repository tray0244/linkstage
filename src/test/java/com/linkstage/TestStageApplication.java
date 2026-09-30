package com.linkstage;

import org.springframework.boot.SpringApplication;

public class TestStageApplication {

	public static void main(String[] args) {
		SpringApplication.from(StageApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
