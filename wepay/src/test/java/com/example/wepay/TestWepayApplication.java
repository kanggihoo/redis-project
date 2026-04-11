package com.example.wepay;

import org.springframework.boot.SpringApplication;

public class TestWepayApplication {

	public static void main(String[] args) {
		SpringApplication.from(WepayApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
