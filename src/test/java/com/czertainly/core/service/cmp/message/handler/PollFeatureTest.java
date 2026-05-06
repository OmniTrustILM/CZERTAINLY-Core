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
 * <p>Pinned outcomes of the {@link PollResult} contract:</p>
 * <ul>
 *   <li>Cert in expected state → {@link PollResult.Reached};</li>
 *   <li>Cert in {@code PENDING_ISSUE} / {@code PENDING_REVOKE} → {@link PollResult.StillPending};</li>
 *   <li>Cert in a terminal state that is <em>not</em> the expected one (e.g. {@code FAILED}
 *       observed while waiting for {@code ISSUED}, the asynchronous-cancel race) →
 *       {@link PollResult.Diverted};</li>
 *   <li>{@link NotFoundException} from the certificate service → {@link CmpProcessingException}
 *       carrying the cause;</li>
 *   <li>Cert never leaves a transitional state within the configured budget → timeout
 *       {@link CmpProcessingException}.</li>
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
        // pollFeatureTimeout is a Spring @Value-injected field; set it via reflection so
        // the test runs without a Spring context.
        Field timeoutField = PollFeature.class.getDeclaredField("pollFeatureTimeout");
        timeoutField.setAccessible(true);
        timeoutField.set(pollFeature, 1);
        Field emField = PollFeature.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(pollFeature, entityManager);
    }

    @Test
    void returnsStillPending_whenCertReachesPendingIssue() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.PENDING_ISSUE);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        PollResult result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.ISSUED);

        assertThat(result).isInstanceOfSatisfying(PollResult.StillPending.class,
                sp -> assertThat(sp.currentState()).isEqualTo(CertificateState.PENDING_ISSUE));
    }

    @Test
    void returnsStillPending_whenCertReachesPendingRevoke() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.PENDING_REVOKE);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        PollResult result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.REVOKED);

        assertThat(result).isInstanceOfSatisfying(PollResult.StillPending.class,
                sp -> assertThat(sp.currentState()).isEqualTo(CertificateState.PENDING_REVOKE));
    }

    @Test
    void returnsReached_whenCertAlreadyInExpectedState() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.ISSUED);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        PollResult result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.ISSUED);

        assertThat(result).isInstanceOfSatisfying(PollResult.Reached.class,
                r -> assertThat(r.certificate()).isSameAs(cert));
    }

    @Test
    void returnsDiverted_whenCertEndsInTerminalStateNotEqualToExpected() throws Exception {
        // Race: cert was diverted to FAILED while waiting for ISSUED — typically because an
        // operator-driven cancelPendingCertificateOperation transitioned the cert mid-poll.
        // Caller must reject the operation cleanly rather than time out with systemFailure.
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.FAILED);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        PollResult result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.ISSUED);

        assertThat(result).isInstanceOfSatisfying(PollResult.Diverted.class,
                d -> assertThat(d.currentState()).isEqualTo(CertificateState.FAILED));
    }

    @Test
    void returnsDiverted_whenCertEndsInIssuedButExpectedRevoked() throws Exception {
        // Equivalent race in the revoke direction: a concurrent cancelPendingCertificateOperation
        // (cancel-revoke) sets the cert back to ISSUED while this poll waits for REVOKED.
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.ISSUED);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        PollResult result = pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.REVOKED);

        assertThat(result).isInstanceOfSatisfying(PollResult.Diverted.class,
                d -> assertThat(d.currentState()).isEqualTo(CertificateState.ISSUED));
    }

    @Test
    void wrapsNotFoundException_withCmpProcessingException() throws Exception {
        UUID certUuid = UUID.randomUUID();
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenThrow(new NotFoundException(Certificate.class, certUuid));

        assertThatThrownBy(() -> pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.ISSUED))
                .isInstanceOf(CmpProcessingException.class)
                .hasCauseInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsCmpProcessingException_whenTimeoutAndStillTransitional() throws Exception {
        // Cert in REQUESTED state never reaches ISSUED; timeout config is 1s in setUp().
        UUID certUuid = UUID.randomUUID();
        Certificate cert = certificateInState(certUuid, CertificateState.REQUESTED);
        Mockito.when(certificateService.getCertificateEntity(Mockito.any(SecuredUUID.class)))
                .thenReturn(cert);

        assertThatThrownBy(() -> pollFeature.pollCertificate(
                new DEROctetString(new byte[]{1}), "01", certUuid.toString(), CertificateState.ISSUED))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("polling timed out");
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
