package com.czertainly.core.util.serialnumber;

import com.czertainly.core.util.clocksource.ClockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SerialNumberConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SerialNumberConfiguration.class);

    @Bean
    SerialNumberGenerator serialNumberGenerator(ClockSource clockSource) {
        var resolution = InstanceIdResolver.resolve();
        if (resolution.source() == InstanceIdResolver.Source.ENV_VAR) {
            log.info("Instance ID resolved from {} environment variable: {}", InstanceIdResolver.ENV_VAR, resolution.id());
        } else {
            log.info("Instance ID resolved from IP address: {}", resolution.id());
        }
        return new SnowflakeSerialNumberGenerator(clockSource, resolution.id());
    }
}
