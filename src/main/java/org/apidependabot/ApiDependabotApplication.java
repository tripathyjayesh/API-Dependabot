package org.apidependabot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiDependabotApplication {
    public static void main(String[] args) {
        if (requestsJsonOutput(args)) {
            // Keep machine-readable stdout free of Spring's banner and startup logs.
            System.setOut(System.err);
        }
        SpringApplication.run(ApiDependabotApplication.class, args);
    }

    private static boolean requestsJsonOutput(String[] args) {
        for (int index = 0; index < args.length; index++) {
            if ("--format=json".equals(args[index])) {
                return true;
            }
            if ("--format".equals(args[index]) && index + 1 < args.length && "json".equalsIgnoreCase(args[index + 1])) {
                return true;
            }
        }
        return false;
    }
}
