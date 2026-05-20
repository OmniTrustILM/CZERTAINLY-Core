package com.czertainly.core.service.tsa.timequality;

import com.czertainly.api.model.messaging.timequality.LeapSecondWarning;
import com.czertainly.api.model.messaging.timequality.TimeQualityStatus;

import java.time.Instant;
import java.util.List;

public record TimeQualityResult(
        String profile,
        Instant timestamp,
        TimeQualityStatus status,
        Double measuredDriftMs,
        int reachableServers,
        String reason,
        LeapSecondWarning leapSecondWarning,
        List<NtpServerResult> servers) {
}
