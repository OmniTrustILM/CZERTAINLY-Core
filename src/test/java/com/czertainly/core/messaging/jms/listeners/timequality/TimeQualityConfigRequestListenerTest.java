package com.czertainly.core.messaging.jms.listeners.timequality;

import com.czertainly.api.model.messaging.timequality.TimeQualityConfigRequestMessage;
import com.czertainly.core.dao.entity.signing.TimeQualityConfiguration;
import com.czertainly.core.dao.repository.signing.TimeQualityConfigurationRepository;
import com.czertainly.core.messaging.jms.producers.TimeQualityConfigurationProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeQualityConfigRequestListenerTest {

    @Mock TimeQualityConfigurationRepository repository;
    @Mock TimeQualityConfigurationProducer producer;

    @InjectMocks TimeQualityConfigRequestListener listener;

    @Test
    void processMessage_publishesSnapshotOfAllStoredConfigs() {
        TimeQualityConfiguration config = new TimeQualityConfiguration();
        config.setName("my-profile");
        when(repository.findAll()).thenReturn(List.of(config));

        TimeQualityConfigRequestMessage request = new TimeQualityConfigRequestMessage();
        request.setRequestedAt(Instant.now());

        listener.processMessage(request);

        verify(producer).publishSnapshot(List.of(config));
    }

    @Test
    void processMessage_withEmptyDb_publishesEmptySnapshot() {
        when(repository.findAll()).thenReturn(List.of());

        listener.processMessage(new TimeQualityConfigRequestMessage());

        verify(producer).publishSnapshot(List.of());
    }
}
