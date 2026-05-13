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
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

/**
 * Regression tests for issue #1455.
 *
 * <p>When the compliance provider fails, {@code validationStatus} must not be reset to
 * {@code NOT_CHECKED} and there must be no duplicate "Update Validation Status" event
 * history entries.
 */
class CertificateHandlerComplianceFailureTest extends BaseComplianceTest {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertificateHandler certificateHandler;

    @Autowired
    private CertificateEventHistoryRepository certificateEventHistoryRepository;

    /**
     * Issue #1455: a 500 from the compliance provider must not reset {@code validationStatus}
     * to {@code NOT_CHECKED} and must not produce duplicate "Update Validation Status"
     * event history entries.
     */
    @Test
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

        // Associate certificate with the RA profile that has a compliance profile
        Certificate cert = certificateRepository.findByUuid(UUID.fromString(certDto.getUuid())).orElseThrow();
        cert.setRaProfileUuid(associatedRaProfileUuid);
        certificateRepository.save(cert);

        // Load with associations exactly as ValidationListener.processMessage() does
        Certificate certWithAssociations = certificateRepository
                .findAllWithAssociationsByUuidIn(List.of(cert.getUuid()))
                .getFirst();

        // Invoke the same flow that ValidationListener triggers
        certificateHandler.validate(certWithAssociations);

        Certificate reloaded = certificateRepository.findByUuid(cert.getUuid()).orElseThrow();

        // Compliance provider returned 500 → complianceStatus must reflect the failure
        Assertions.assertEquals(ComplianceStatus.FAILED, reloaded.getComplianceStatus(),
                "complianceStatus should be FAILED when the compliance provider returns 500");

        // Issue #1455: compliance failure must NOT overwrite the result of X.509 validation.
        // validationStatus must be whatever the X.509 validator set — NOT NOT_CHECKED.
        Assertions.assertNotEquals(CertificateValidationStatus.NOT_CHECKED, reloaded.getValidationStatus(),
                "validationStatus must not be reset to NOT_CHECKED after a compliance check failure (issue #1455)");

        // Issue #1455: only one UPDATE_VALIDATION_STATUS history entry expected, not 3 duplicates
        List<CertificateEventHistory> history = certificateEventHistoryRepository
                .findByCertificateOrderByCreatedDesc(reloaded);
        long validationStatusEntries = history.stream()
                .filter(h -> h.getEvent() == CertificateEvent.UPDATE_VALIDATION_STATUS)
                .count();
        Assertions.assertEquals(1L, validationStatusEntries,
                "Expected exactly 1 UPDATE_VALIDATION_STATUS event; found " + validationStatusEntries
                        + " (issue #1455: duplicate entries)");
    }
}
