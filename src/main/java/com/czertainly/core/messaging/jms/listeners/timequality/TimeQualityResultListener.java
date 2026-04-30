package com.czertainly.core.messaging.jms.listeners.timequality;

import com.czertainly.api.model.messaging.timequality.*;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.jms.listeners.MessageProcessor;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.tsa.timequality.*;
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
        if (timeQualityConfigurationRepository.findByUuid(SecuredUUID.fromUUID(message.getId())).isEmpty()) {
            log.warn("Received time quality result for unknown profile ID={}, dropping", message.getId());
            return;
        }

        TimeQualityResult result = new TimeQualityResult(
                message.getName(),
                message.getTimestamp(),
                toStatus(message.getStatus()),
                message.getMeasuredDriftMs(),
                message.getReachableServers(),
                message.getReason(),
                toLeapSecondWarning(message.getLeapSecondWarning()),
                toNtpServerResults(message.getServers())
        );
        log.debug("Received time quality result {}", result);
        timeQualityRegister.update(result);
    }

    private static TimeQualityStatus toStatus(TimeQualityStatusMessage status) {
        return switch (status) {
            case OK -> TimeQualityStatus.OK;
            case DEGRADED -> TimeQualityStatus.DEGRADED;
        };
    }

    private static LeapSecondWarning toLeapSecondWarning(LeapSecondWarningMessage warning) {
        return switch (warning) {
            case NONE -> LeapSecondWarning.NONE;
            case POSITIVE -> LeapSecondWarning.POSITIVE;
            case NEGATIVE -> LeapSecondWarning.NEGATIVE;
        };
    }

    private static List<NtpServerResult> toNtpServerResults(List<NtpServerMessage> servers) {
        if (servers == null) return List.of();
        return servers.stream()
                .map(s -> new NtpServerResult(s.getHost(), s.isReachable(), s.getOffsetMs(), s.getRttMs(), s.getStratum(), s.getPrecisionMs()))
                .toList();
    }
}
