package com.systekna.obsidian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ObsidianClaudeApp {

    public static void main(String[] args) {
        SpringApplication.run(ObsidianClaudeApp.class, args);
    }
}
