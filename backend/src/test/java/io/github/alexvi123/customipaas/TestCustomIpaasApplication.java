package io.github.alexvi123.customipaas;

import org.springframework.boot.SpringApplication;

public class TestCustomIpaasApplication {

	public static void main(String[] args) {
		SpringApplication.from(CustomIpaasApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
