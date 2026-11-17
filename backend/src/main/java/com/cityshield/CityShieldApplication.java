package com.cityshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CityShield - academic simulation of a smart-city emergency response system.
 * Not a live emergency dispatch service; all data is synthetic.
 */
@SpringBootApplication
public class CityShieldApplication {
    public static void main(String[] args) {
        SpringApplication.run(CityShieldApplication.class, args);
    }
}
