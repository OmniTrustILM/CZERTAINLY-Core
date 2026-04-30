package com.czertainly.core.messaging.jms.listeners.timequality;

import com.czertainly.api.model.messaging.timequality.*;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.tsa.timequality.TimeQualityRegister;
import com.czertainly.core.service.tsa.timequality.TimeQualityResult;
import com.czertainly.core.service.tsa.timequality.TimeQualityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeQualityResultListenerTest {

    @Mock TimeQualityConfigurationRepository repository;
    @Mock TimeQualityRegister register;

    @InjectMocks TimeQualityResultListener listener;

    @Test
    void processMessage_withKnownId_updatesRegister() {
        UUID id = UUID.randomUUID();
        TimeQualityConfiguration entity = new TimeQualityConfiguration();
        entity.setName("known");
        when(repository.findByUuid(any(SecuredUUID.class))).thenReturn(Optional.of(entity));

        listener.processMessage(buildResult(id, "known", TimeQualityStatusMessage.OK));

        ArgumentCaptor<TimeQualityResult> captor = ArgumentCaptor.forClass(TimeQualityResult.class);
        verify(register).update(captor.capture());
        assertThat(captor.getValue().profile()).isEqualTo("known");
        assertThat(captor.getValue().status()).isEqualTo(TimeQualityStatus.OK);
    }

    @Test
    void processMessage_withUnknownId_dropsMessage() {
        UUID id = UUID.randomUUID();
        when(repository.findByUuid(any(SecuredUUID.class))).thenReturn(Optional.empty());

        listener.processMessage(buildResult(id, "unknown", TimeQualityStatusMessage.OK));

        verifyNoInteractions(register);
    }

    @Test
    void processMessage_withDegradedStatus_registersCorrectStatus() {
        UUID id = UUID.randomUUID();
        TimeQualityConfiguration entity = new TimeQualityConfiguration();
        entity.setName("degraded");
        when(repository.findByUuid(any(SecuredUUID.class))).thenReturn(Optional.of(entity));

        listener.processMessage(buildResult(id, "degraded", TimeQualityStatusMessage.DEGRADED));

        ArgumentCaptor<TimeQualityResult> captor = ArgumentCaptor.forClass(TimeQualityResult.class);
        verify(register).update(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(TimeQualityStatus.DEGRADED);
    }

    private TimeQualityResultMessage buildResult(UUID id, String name, TimeQualityStatusMessage status) {
        NtpServerMessage server = new NtpServerMessage();
        server.setHost("pool.ntp.org");
        server.setReachable(true);
        server.setOffsetMs(0.0);
        server.setRttMs(1.0);
        server.setStratum(2);
        server.setPrecisionMs(0.1);

        TimeQualityResultMessage msg = new TimeQualityResultMessage();
        msg.setId(id);
        msg.setName(name);
        msg.setTimestamp(Instant.now());
        msg.setStatus(status);
        msg.setMeasuredDriftMs(0.0);
        msg.setReachableServers(1);
        msg.setLeapSecondWarning(LeapSecondWarningMessage.NONE);
        msg.setServers(List.of(server));
        return msg;
    }
}
