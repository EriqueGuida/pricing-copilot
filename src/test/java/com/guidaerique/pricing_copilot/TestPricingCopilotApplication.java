package com.guidaerique.pricing_copilot;

import org.springframework.boot.SpringApplication;

public class TestPricingCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.from(PricingCopilotApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
