package edu.cit.balacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Parent application class. Living in edu.cit.balacy (the common parent
 * package of all modules) means component scanning picks up every module
 * from a single entry point, while each module's package still acts as its
 * own boundary. @EnableScheduling drives the supplier module's jobs.
 */
@SpringBootApplication
@EnableScheduling
public class Activity02Application {

	public static void main(String[] args) {
		SpringApplication.run(Activity02Application.class, args);
	}

}
