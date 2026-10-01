package edu.cit.balacy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class AppInstanceConfig {

    private static final Logger log = LoggerFactory.getLogger(AppInstanceConfig.class);
    private final String instanceId = UUID.randomUUID().toString();

    @Bean
    public String clientInstanceId() {
        log.info("Application starting with instance ID: {}", instanceId);
        return instanceId;
    }
}
