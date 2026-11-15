package com.ex.tradingPlatform;

import org.springframework.boot.SpringApplication;

public class TestTradingPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(TradingPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
