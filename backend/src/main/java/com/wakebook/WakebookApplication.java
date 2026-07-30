package com.wakebook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WakebookApplication {

	public static void main(String[] args) {
		SpringApplication.run(WakebookApplication.class, args);
	}

}
