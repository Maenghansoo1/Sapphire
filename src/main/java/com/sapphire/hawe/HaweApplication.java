package com.sapphire.hawe;

import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "com.sapphire.hawe", annotationClass = Mapper.class)
@EnableScheduling
public class HaweApplication {

	public static void main(String[] args) {
		SpringApplication.run(HaweApplication.class, args);
	}

}
