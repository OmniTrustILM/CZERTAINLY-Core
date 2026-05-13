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
 * End-to-end tests for the certificate validation and compliance flow through {@link CertificateHandler}.
 *
 * <p>A real RabbitMQ container is required ({@code messaging-int-test} profile with
 * {@code inheritProfiles = false}) so that JMS endpoint configs annotated
 * {@code @Profile("!test")} are loaded and the {@code CERTIFICATE_STATUS_CHANGED} event
 * is actually delivered end-to-end and recorded in certificate event history.
 */
@Testcontainers
@ActiveProfiles(value = {"messaging-int-test"}, inheritProfiles = false)
class CertificateHandlerValidationAndComplianceTest extends BaseComplianceTest {

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

        // Remove internal compliance rules from the profile added by the base class.
        complianceProfileRuleRepository.deleteAll(
                complianceProfileRuleRepository.findAll().stream()
                        .filter(r -> r.getInternalRuleUuid() != null)
                        .toList()
        );
    }

    @AfterEach
    void tearDownAsync() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }

    @Test
    void validateAndCheckComplianceWithSuccessfulProvider() throws Exception {
        WireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(
                        "/v2/complianceProvider/%s/compliance".formatted(KIND_V2)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "rules": [
                                    {
                                      "uuid": "%s",
                                      "name": "Rule1",
                                      "status": "ok"
                                    }
                                  ]
                                }
                                """.formatted(complianceV2RuleUuid))));

        var certInfo = CertificateGeneratorHelper.generateCertificateWithIssuer(
                KeyAlgorithm.RSA, "CN=Test-Issuer", "CN=Test-Subject", null);
        UploadCertificateRequestDto uploadDto = new UploadCertificateRequestDto();
        uploadDto.setCertificate(certInfo.getCaCertificateBase64Encoded());
        certificateService.upload(uploadDto, true);
        uploadDto.setCertificate(certInfo.getEndEntityCertificateBase64Encoded());
        var certDto = certificateService.upload(uploadDto, true);

        UUID certUuid = UUID.fromString(certDto.getUuid());

        // Wait for the initial async X.509 validation triggered by upload to complete before associating the RA profile.
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                certificateRepository.findByUuid(certUuid)
                        .map(c -> c.getValidationStatus() != CertificateValidationStatus.NOT_CHECKED)
                        .orElse(false)
        );

        // Associate certificate with the RA profile that has a compliance profile.
        Certificate cert = certificateRepository.findByUuid(certUuid).orElseThrow();
        cert.setRaProfileUuid(associatedRaProfileUuid);
        certificateRepository.save(cert);

        // Load with associations exactly as ValidationListener.processMessage() does.
        Certificate certWithAssociations = certificateRepository
                .findAllWithAssociationsByUuidIn(List.of(cert.getUuid()))
                .getFirst();

        certificateHandler.validate(certWithAssociations);

        Certificate reloaded = certificateRepository.findByUuid(cert.getUuid()).orElseThrow();

        Assertions.assertEquals(ComplianceStatus.OK, reloaded.getComplianceStatus(),
                "complianceStatus should be OK when the compliance provider returns all rules passing");

        Assertions.assertNotEquals(CertificateValidationStatus.NOT_CHECKED, reloaded.getValidationStatus(),
                "validationStatus must reflect the completed X.509 validation outcome");

        // Wait for the CERTIFICATE_STATUS_CHANGED JMS event to be delivered end-to-end
        // (producer → RabbitMQ → EventListener → history writer).
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CertificateEventHistory> history = certificateEventHistoryRepository
                    .findByCertificateOrderByCreatedDesc(reloaded);
            long validationStatusEntries = history.stream()
                    .filter(h -> h.getEvent() == CertificateEvent.UPDATE_VALIDATION_STATUS)
                    .count();
            Assertions.assertEquals(1L, validationStatusEntries,
                    "Expected exactly one UPDATE_VALIDATION_STATUS event; found " + validationStatusEntries);
        });
    }

    @Test
    void validateAndCheckComplianceWithUnavailableProvider() throws Exception {
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

        // Wait for the initial async X.509 validation triggered by upload to complete before associating the RA profile.
        await().atMost(10, TimeUnit.SECONDS).until(() ->
                certificateRepository.findByUuid(certUuid)
                        .map(c -> c.getValidationStatus() != CertificateValidationStatus.NOT_CHECKED)
                        .orElse(false)
        );

        // Associate certificate with the RA profile that has a compliance profile.
        Certificate cert = certificateRepository.findByUuid(certUuid).orElseThrow();
        cert.setRaProfileUuid(associatedRaProfileUuid);
        certificateRepository.save(cert);

        // Load with associations exactly as ValidationListener.processMessage() does
        Certificate certWithAssociations = certificateRepository
                .findAllWithAssociationsByUuidIn(List.of(cert.getUuid()))
                .getFirst();

        certificateHandler.validate(certWithAssociations);

        Certificate reloaded = certificateRepository.findByUuid(cert.getUuid()).orElseThrow();

        // Compliance provider returned 500 → complianceStatus must reflect the failure
        Assertions.assertEquals(ComplianceStatus.FAILED, reloaded.getComplianceStatus(),
                "complianceStatus should be FAILED when the compliance provider returns 500");

        Assertions.assertEquals(CertificateValidationStatus.INVALID, reloaded.getValidationStatus());

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