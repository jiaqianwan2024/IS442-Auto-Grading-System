package com.autogradingsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * Main - Spring Boot Entry Point
 *
 * MODE:
 *   Web UI only → run-web.bat / ./run-web.sh → http://localhost:8080
 */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
