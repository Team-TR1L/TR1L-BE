package com.tr1l.dispatchserver.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.tr1l.dispatch.infra.persistence.repository")
@EntityScan(basePackages = "com.tr1l.dispatch")
public class DispatchJpaConfig {
}