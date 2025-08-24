package com.moniepoint.kv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI kvOpenAPI() {
		return new OpenAPI().info(new Info().title("Moniepoint KV Store API").version("1.0")
				.description("Simple persistent key-value store (PUT/GET/DELETE/RANGE/BATCH)"));
	}

}
