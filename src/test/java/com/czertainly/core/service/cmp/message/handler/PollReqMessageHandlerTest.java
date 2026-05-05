package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.CmpTransactionService;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIConfirmContent;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIHeaderBuilder;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.PollReqContent;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PollReqMessageHandler}.
 *
 * <p>Pinned cases:</p>
 * <ul>
 *   <li>A pollReq for a transaction whose originating body was a revocation request must
 *       be cleanly rejected: wrapping the response in {@code CertRepMessage} regardless of
 *       original type would produce an invalid CMP body for revocation polls.</li>
 *   <li>A pollReq landing on a {@code PENDING_REVOKE} certificate must be rejected — RFC
 *       4210 §5.2.6 limits polling to ip/cp/kup contexts (issue/renew/rekey); CMP has no
 *       in-protocol way to represent a pending revocation.</li>
 * </ul>
 */
class PollReqMessageHandlerTest {

    private CmpTransactionService cmpTransactionService;
    private PollReqMessageHandler handler;
    private ConfigurationContext configuration;

    @BeforeEach
    void setUp() {
        cmpTransactionService = Mockito.mock(CmpTransactionService.class);
        handler = new PollReqMessageHandler();
        handler.setCmpTransactionService(cmpTransactionService);

        // Minimal configuration; the rejection paths exercised below short-circuit before
        // any message building, so no CmpProfile / shared secret / PkiMessageBuilder is
        // exercised — keep this trivial to avoid pulling in encryption setup.
        configuration = Mockito.mock(ConfigurationContext.class);
    }

    @Test
    void rejectsRevocationPoll_whenTransactionOriginatedFromRevocationRequest() {
        // The handler must not wrap a revocation-originated pollReq response in
        // CertRepMessage — that body type+content combination is invalid CMP. Reject
        // these polls explicitly instead.
        CmpTransaction trx = transactionWithCert(certificateInState(CertificateState.PENDING_REVOKE));
        trx.setOriginalRequestBodyType(PKIBody.TYPE_REVOCATION_REQ);
        Mockito.when(cmpTransactionService.findByTransactionId(Mockito.anyString()))
                .thenReturn(List.of(trx));

        PKIMessage pollReq = pollReqMessage();

        assertThatThrownBy(() -> handler.handle(pollReq, configuration))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("not supported for revocation transactions");
    }

    @Test
    void rejectsPoll_whenCertificateInPendingRevoke_andNoOriginalBodyType() {
        // Defensive case: legacy transactions where originalRequestBodyType is NULL but
        // the certificate is somehow in PENDING_REVOKE — must not silently emit a CMP body
        // that doesn't correspond to the original operation.
        CmpTransaction trx = transactionWithCert(certificateInState(CertificateState.PENDING_REVOKE));
        trx.setOriginalRequestBodyType(null);
        Mockito.when(cmpTransactionService.findByTransactionId(Mockito.anyString()))
                .thenReturn(List.of(trx));

        PKIMessage pollReq = pollReqMessage();

        assertThatThrownBy(() -> handler.handle(pollReq, configuration))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("PENDING_REVOKE");
    }

    @Test
    void rejectsPoll_whenNoTransactionForTransactionId() {
        Mockito.when(cmpTransactionService.findByTransactionId(Mockito.anyString()))
                .thenReturn(List.of());

        PKIMessage pollReq = pollReqMessage();

        assertThatThrownBy(() -> handler.handle(pollReq, configuration))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("no in-flight CMP transaction");
    }

    @Test
    void rejectsPoll_whenCertificateInTerminalState() {
        for (CertificateState terminal : List.of(CertificateState.REVOKED, CertificateState.REJECTED, CertificateState.FAILED)) {
            CmpTransaction trx = transactionWithCert(certificateInState(terminal));
            trx.setOriginalRequestBodyType(PKIBody.TYPE_INIT_REQ);
            Mockito.when(cmpTransactionService.findByTransactionId(Mockito.anyString()))
                    .thenReturn(List.of(trx));

            PKIMessage pollReq = pollReqMessage();

            assertThatThrownBy(() -> handler.handle(pollReq, configuration))
                    .isInstanceOf(CmpProcessingException.class)
                    .hasMessageContaining("client should not poll");
        }
    }

    @Test
    void rejectsPoll_withWrongBodyType() {
        // The handler must guard against being called with a non-pollReq body — protects
        // against accidental wiring elsewhere in the dispatch chain.
        PKIHeader header = pkiHeader();
        PKIMessage notAPollReq = new PKIMessage(header,
                new PKIBody(PKIBody.TYPE_CONFIRM, new PKIConfirmContent()));

        assertThatThrownBy(() -> handler.handle(notAPollReq, configuration))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("pollReq handler invoked for wrong body type");
    }

    @Test
    void rejectsPoll_withEmptyCertReqIdValues() {
        PKIHeader header = pkiHeader();
        PKIMessage emptyPollReq = new PKIMessage(header,
                new PKIBody(PKIBody.TYPE_POLL_REQ, new PollReqContent(new ASN1Integer[0])));

        assertThatThrownBy(() -> handler.handle(emptyPollReq, configuration))
                .isInstanceOf(CmpProcessingException.class)
                .hasMessageContaining("no certReqId");
    }

    private static Certificate certificateInState(CertificateState state) {
        Certificate cert = new Certificate();
        cert.setUuid(UUID.randomUUID());
        cert.setState(state);
        cert.setSerialNumber("01");
        cert.setSubjectDn("CN=test");
        // Cert content not exercised in the rejection paths above; left null on purpose.
        cert.setCertificateContent((CertificateContent) null);
        return cert;
    }

    private static CmpTransaction transactionWithCert(Certificate cert) {
        CmpTransaction trx = new CmpTransaction();
        trx.setUuid(UUID.randomUUID());
        trx.setCertificate(cert);
        trx.setCertificateUuid(cert.getUuid());
        trx.setTransactionId("test-tid");
        return trx;
    }

    private static PKIHeader pkiHeader() {
        return new PKIHeaderBuilder(
                PKIHeader.CMP_2000,
                new GeneralName(new X500Name("CN=test-sender")),
                new GeneralName(new X500Name("CN=test-recipient")))
                .setTransactionID(new DEROctetString(new byte[]{1, 2, 3, 4}))
                .build();
    }

    private static PKIMessage pollReqMessage() {
        return new PKIMessage(pkiHeader(),
                new PKIBody(PKIBody.TYPE_POLL_REQ, new PollReqContent(new ASN1Integer(BigInteger.ZERO))));
    }
}
