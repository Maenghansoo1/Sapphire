package com.sapphire.hawe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sapphire.hawe")
public class HaweApplication {

	public static void main(String[] args) {
		SpringApplication.run(HaweApplication.class, args);
	}

}
