package com.nexora.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled methods (nightly checks and clean-ups). Jobs run inside this one application:
// no external scheduler is needed for a single-server deployment.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
