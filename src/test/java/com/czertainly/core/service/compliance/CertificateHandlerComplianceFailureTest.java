package com.czertainly.core.service.compliance;

import com.czertainly.api.model.client.certificate.UploadCertificateRequestDto;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.core.certificate.CertificateEvent;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.compliance.ComplianceStatus;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateEventHistory;
import com.czertainly.core.dao.repository.CertificateEventHistoryRepository;
import com.czertainly.core.helpers.CertificateGeneratorHelper;
import com.czertainly.core.service.CertificateService;
import com.czertainly.core.service.handler.CertificateHandler;
import com.czertainly.core.util.RabbitMQContainerFactory;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Regression tests for issue #1455.
 *
 * <p>When the compliance provider fails, {@code validationStatus} must not be reset to
 * {@code NOT_CHECKED} and there must be no duplicate "Update Validation Status" event
 * history entries.
 *
 * <p>A real RabbitMQ container is required ({@code messaging-int-test} profile with
 * {@code inheritProfiles = false}) so that JMS endpoint configs annotated
 * {@code @Profile("!test")} are loaded and the {@code CERTIFICATE_STATUS_CHANGED} event
 * is actually delivered end-to-end and recorded in certificate event history.
 */
@Testcontainers
@ActiveProfiles(value = {"messaging-int-test"}, inheritProfiles = false)
class CertificateHandlerComplianceFailureTest extends BaseComplianceTest {

    @Container
    private static final RabbitMQContainer rabbitMQContainer = RabbitMQContainerFactory.create();

    @DynamicPropertySource
    static void rabbitMQProperties(DynamicPropertyRegistry registry) throws IOException, InterruptedException {
        RabbitMQContainerFactory.importDefinitions(rabbitMQContainer);
        registry.add("spring.messaging.broker-url",
                () -> "amqp://%s:%d".formatted(rabbitMQContainer.getHost(), rabbitMQContainer.getAmqpPort()));
        registry.add("spring.messaging.broker-type", () -> "RABBITMQ");
        registry.add("spring.messaging.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.messaging.password", rabbitMQContainer::getAdminPassword);
        // Disable the per-instance and shared proxy queue listeners — the test-instance queue
        // does not exist in the test RabbitMQ definitions and would cause an infinite retry loop.
        registry.add("proxy.enabled", () -> "false");
    }

    @Autowired
    private CertificateHandler certificateHandler;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateEventHistoryRepository certificateEventHistoryRepository;

    @BeforeEach
    void setUpAsync() {
        // JMS listener threads must inherit the security context set up by BaseSpringBootTest.setupAuth().
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        SecurityContextHolder.getContext().setAuthentication(getAuthentication());
    }

    @AfterEach
    void tearDownAsync() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    /**
     * Issue #1455: a 500 from the compliance provider must not reset {@code validationStatus}
     * to {@code NOT_CHECKED} and must not produce duplicate "Update Validation Status"
     * event history entries.
     *
     * <p>The race being tested: {@code CertificateHandler.validate()} runs under
     * {@code REQUIRES_NEW} (T1).  X.509 validation runs inside T1 and saves its result
     * (not yet committed).  The compliance call has {@code NOT_SUPPORTED}, which suspends
     * T1 and runs compliance without a transaction — so compliance loads the pre-commit
     * DB snapshot and saves its own result in a separate auto-commit transaction (T2).
     * When T1 finally commits, a full-row UPDATE (no {@code @DynamicUpdate}) overwrites
     * T2's {@code complianceStatus} with the stale value from T1's snapshot.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testValidationStatusPreservedWhenComplianceCheckFails() throws Exception {
        // Compliance provider is unavailable — returns 500 for any check request
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(
                        "/v2/complianceProvider/%s/compliance".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // Upload issuer + end-entity so the chain can be verified during X.509 validation
        var certInfo = CertificateGeneratorHelper.generateCertificateWithIssuer(
                KeyAlgorithm.RSA, "CN=Test-Issuer-1455", "CN=Test-Subject-1455", null);
        UploadCertificateRequestDto uploadDto = new UploadCertificateRequestDto();
        uploadDto.setCertificate(certInfo.getCaCertificateBase64Encoded());
        certificateService.upload(uploadDto, true);
        uploadDto.setCertificate(certInfo.getEndEntityCertificateBase64Encoded());
        var certDto = certificateService.upload(uploadDto, true);

        UUID certUuid = UUID.fromString(certDto.getUuid());

        // Each upload fires a @TransactionalEventListener(AFTER_COMMIT) → JMS ValidationMessage →
        // ValidationListener calls certificateHandler.validate() on a listener thread.  At this
        // point the cert has no RA profile, so only X.509 validation runs (no compliance).  Wait
        // for that async validation to commit before associating the RA profile — otherwise the
        // listener thread and the manual validate() call below both start with validationStatus=
        // NOT_CHECKED and both fire CERTIFICATE_STATUS_CHANGED, producing two history entries
        // instead of one.
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                certificateRepository.findByUuid(certUuid)
                        .map(c -> c.getValidationStatus() != CertificateValidationStatus.NOT_CHECKED)
                        .orElse(false)
        );

        // Associate certificate with the RA profile that has a compliance profile
        Certificate cert = certificateRepository.findByUuid(certUuid).orElseThrow();
        cert.setRaProfileUuid(associatedRaProfileUuid);
        certificateRepository.save(cert);

        // Load with associations exactly as ValidationListener.processMessage() does
        Certificate certWithAssociations = certificateRepository
                .findAllWithAssociationsByUuidIn(List.of(cert.getUuid()))
                .getFirst();

        // Reproduce the real production flow: ValidationListener.processMessage() calls
        // certificateHandler.validate(), which wraps both X.509 validation (inside REQUIRES_NEW)
        // and the compliance check (inside NOT_SUPPORTED, suspending REQUIRES_NEW) in a single
        // call.  This is the entry point that creates the transaction/propagation race described
        // in issue #1455.
        certificateHandler.validate(certWithAssociations);

        Certificate reloaded = certificateRepository.findByUuid(cert.getUuid()).orElseThrow();

        // Compliance provider returned 500 → complianceStatus must reflect the failure
        Assertions.assertEquals(ComplianceStatus.FAILED, reloaded.getComplianceStatus(),
                "complianceStatus should be FAILED when the compliance provider returns 500");

        // Issue #1455: compliance failure must NOT overwrite the result of X.509 validation.
        // validationStatus must be whatever the X.509 validator set — NOT NOT_CHECKED.
        Assertions.assertNotEquals(CertificateValidationStatus.NOT_CHECKED, reloaded.getValidationStatus(),
                "validationStatus must not be reset to NOT_CHECKED after a compliance check failure (issue #1455)");

        // Issue #1455: wait for the CERTIFICATE_STATUS_CHANGED JMS event to be delivered
        // end-to-end (producer → RabbitMQ → EventListener → history writer), then assert
        // exactly one UPDATE_VALIDATION_STATUS history entry — not 3 duplicates.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CertificateEventHistory> history = certificateEventHistoryRepository
                    .findByCertificateOrderByCreatedDesc(reloaded);
            long validationStatusEntries = history.stream()
                    .filter(h -> h.getEvent() == CertificateEvent.UPDATE_VALIDATION_STATUS)
                    .count();
            Assertions.assertEquals(1L, validationStatusEntries,
                    "Expected exactly 1 UPDATE_VALIDATION_STATUS event; found " + validationStatusEntries
                            + " (issue #1455: duplicate entries)");
        });
    }
}
