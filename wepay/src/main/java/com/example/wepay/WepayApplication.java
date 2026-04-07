package com.example.wepay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WepayApplication {

	public static void main(String[] args) {
		SpringApplication.run(WepayApplication.class, args);
	}

}
