package com.aitecc.moments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AITECC Moments —— 社团版朋友圈 / 小红书。
 *
 * <p>启动方式：{@code mvn spring-boot:run}，或打包后 {@code java -jar target/aitecc-moments.jar}。
 */
@SpringBootApplication
public class MomentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MomentsApplication.class, args);
    }
}
