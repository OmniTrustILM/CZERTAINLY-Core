package com.czertainly.core.config.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "caching.authentication")
public record AuthCacheProperties(
        int ttlMinutes,
        int maxSize
) {
}
