package com.czertainly.core.messaging.jms.producers;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigSnapshotMessage;
import com.czertainly.api.model.messaging.timequality.TimeQualityConfigurationMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.messaging.jms.configuration.MessagingProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@AllArgsConstructor
@Slf4j
public class TimeQualityConfigurationProducer {

    private final JmsTemplate jmsTemplate;
    private final MessagingProperties messagingProperties;
    private final RetryTemplate producerRetryTemplate;

    public void publishSnapshot(List<TimeQualityConfiguration> configurations) {
        TimeQualityConfigSnapshotMessage message = new TimeQualityConfigSnapshotMessage();
        message.setGeneratedAt(Instant.now());
        message.setConfigurations(configurations.stream().map(this::toMessage).toList());
        log.debug("Publishing time quality config snapshot with {} configurations", message.getConfigurations().size());

        producerRetryTemplate.execute(context -> {
            jmsTemplate.convertAndSend(
                    messagingProperties.produceDestinationTimeQualityConfig(),
                    message,
                    msg -> {
                        msg.setJMSType(messagingProperties.routingKey().timeQualityConfig());
                        return msg;
                    });
            return null;
        });
    }

    private TimeQualityConfigurationMessage toMessage(TimeQualityConfiguration config) {
        TimeQualityConfigurationMessage msg = new TimeQualityConfigurationMessage();
        msg.setId(config.getUuid());
        msg.setName(config.getName());
        msg.setNtpServers(config.getNtpServers());
        msg.setNtpCheckInterval(config.getNtpCheckInterval());
        msg.setNtpSamplesPerServer(Objects.requireNonNullElse(config.getNtpSamplesPerServer(), 0));
        msg.setNtpCheckTimeout(config.getNtpCheckTimeout());
        msg.setNtpServersMinReachable(Objects.requireNonNullElse(config.getNtpServersMinReachable(), 0));
        msg.setMaxClockDrift(config.getMaxClockDrift());
        msg.setLeapSecondGuard(Boolean.TRUE.equals(config.getLeapSecondGuard()));
        return msg;
    }
}
