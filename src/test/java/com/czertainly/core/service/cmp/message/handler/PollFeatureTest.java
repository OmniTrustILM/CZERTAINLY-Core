package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import jakarta.persistence.EntityManager;
import org.bouncycastle.asn1.DEROctetString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PollFeature}.
 *
 * <p>Pinned regressions:</p>
 * <ul>
 *   <li>An asynchronous-completion ({@code PENDING_ISSUE} / {@code PENDING_REVOKE}) state is
 *       returned as {@code null} so the caller emits a CMP {@code pollRep} response — without
 *       this, the poll loop would spin until timeout because no synchronous state change is
 *       expected.</li>
 *   <li>{@link NotFoundException} from the certificate service is wrapped in a
 *       {@link CmpProcessingException} carrying the cause (was previously dropped).</li>
 * </ul>
 */
class PollFeatureTest {

    private CertificateService certificateService;
    private EntityManager entityManager;
    private PollFeature pollFeature;

    @BeforeEach
    void setUp() throws Exception {
        certificateService = Mockito.mock(CertificateService.class);
        entityManager = Mockito.mock(EntityManager.class);
        pollFeature = new PollFeature();
        pollFeature.setCertificateService(certificateService);
        // pollFeatureTimeout is a Spring @Value-injected field; set it via reflection
        // so the test runs without a Spring context.
        Field timeoutField = PollFeature.class.getDeclaredField("pollFeatureTimeout");
        timeoutField.setAccessible(true);
        timeoutField.set(pollFeature, 1);
        Field emField = PollFeature.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(pollFeature, entityManager);
    }

    @Test
    void returnsNull_whenCertReachesPendingIssue() throws Exception {
        // Pin: PENDING_ISSUE breaks the poll loop and signals the caller to emit pollRep.
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.PENDING_ISSUE);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        Certificate result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}),
                "01",
                certUuid.toString(),
                CertificateState.ISSUED);

        assertThat(result).isNull();
    }

    @Test
    void returnsNull_whenCertReachesPendingRevoke() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.PENDING_REVOKE);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        Certificate result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}),
                "01",
                certUuid.toString(),
                CertificateState.REVOKED);

        assertThat(result).isNull();
    }

    @Test
    void returnsCert_whenCertAlreadyInExpectedState() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.ISSUED);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        Certificate result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}),
                "01",
                certUuid.toString(),
                CertificateState.ISSUED);

        assertThat(result).isSameAs(cert);
    }

    @Test
    void wrapsNotFoundException_withCmpProcessingException() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenThrow(new NotFoundException(Certificate.class, certUuid));

        assertThatThrownBy(() -> pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}),
                "01",
                certUuid.toString(),
                CertificateState.ISSUED))
                .isInstanceOf(CmpProcessingException.class)
                .hasCauseInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsCmpProcessingException_whenTimeoutAndNotInExpectedState() throws Exception {
        // Cert in REQUESTED state never reaches ISSUED; timeout config is 1s in setUp().
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.REQUESTED);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        assertThatThrownBy(() -> pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}),
                "01",
                certUuid.toString(),
                CertificateState.ISSUED))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("polled certificate is not at valid state");
    }

    private static Certificate certificateInState(UUID uuid, CertificateState state) {
        Certificate cert = new Certificate();
        cert.setUuid(uuid);
        cert.setState(state);
        cert.setSerialNumber("01");
        cert.setSubjectDn("CN=test");
        return cert;
    }
}
