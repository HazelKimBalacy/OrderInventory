package edu.cit.balacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Parent application class. Living in edu.cit.balacy (the common parent
 * package of edu.cit.balacy.shop and edu.cit.balacy.inventory) means
 * component scanning picks up both modules from a single entry point,
 * while each module's package still acts as its own boundary.
 */
@SpringBootApplication
public class Activity02Application {

	public static void main(String[] args) {
		SpringApplication.run(Activity02Application.class, args);
	}

}
