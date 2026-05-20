package com.czertainly.core.messaging.jms.listeners.timequality;

import com.czertainly.api.model.messaging.timequality.NtpServerMeasurementResult;
import com.czertainly.api.model.messaging.timequality.TimeQualityResultMessage;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.tsa.timequality.NtpServerResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityRegister;
import com.czertainly.core.service.tsa.timequality.TimeQualityResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AllArgsConstructor
public class TimeQualityResultListener implements MessageProcessor<TimeQualityResultMessage> {

    private final TimeQualityConfigurationRepository timeQualityConfigurationRepository;
    private final TimeQualityRegister timeQualityRegister;

    @Override
    public void processMessage(TimeQualityResultMessage message) {
        if (timeQualityConfigurationRepository.findByUuid(SecuredUUID.fromUUID(message.getConfigurationId())).isEmpty()) {
            log.warn("Received time quality result for unknown profile ID={}, dropping", message.getConfigurationId());
            return;
        }

        TimeQualityResult result = new TimeQualityResult(
                message.getName(),
                message.getTimestamp(),
                message.getStatus(),
                message.getMeasuredDriftMs(),
                message.getReachableServers(),
                message.getReason(),
                message.getLeapSecondWarning(),
                toNtpServerResults(message.getMeasurements())
        );
        log.debug("Received time quality result {}", result);
        timeQualityRegister.update(result);
    }

    private static List<NtpServerResult> toNtpServerResults(List<NtpServerMeasurementResult> servers) {
        if (servers == null) return List.of();
        return servers.stream()
                .map(s -> new NtpServerResult(s.getHost(), s.isReachable(), s.getOffsetMs(), s.getRttMs(), s.getStratum(), s.getPrecisionMs()))
                .toList();
    }
}
