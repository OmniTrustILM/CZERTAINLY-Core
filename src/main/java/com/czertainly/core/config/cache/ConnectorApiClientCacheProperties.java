package com.czertainly.core.config.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "caching.connectors")
public record ConnectorApiClientCacheProperties(
        int ttlMinutes,
        int maxSize
) {
}
