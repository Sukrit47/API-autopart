package com.pkshop;

import com.pkshop.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.Locale;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class  PkshopApplication {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        SpringApplication.run(PkshopApplication.class, args);
    }
}
