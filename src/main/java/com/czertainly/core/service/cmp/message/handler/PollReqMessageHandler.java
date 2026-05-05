package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.interfaces.core.cmp.error.CmpBaseException;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.cmp.CmpTransaction;
import com.czertainly.core.service.cmp.configurations.ConfigurationContext;
import com.czertainly.core.service.cmp.message.CmpTransactionService;
import com.czertainly.core.service.cmp.message.builder.PkiMessageBuilder;
import com.czertainly.core.util.CertificateUtil;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.CertOrEncCert;
import org.bouncycastle.asn1.cmp.CertResponse;
import org.bouncycastle.asn1.cmp.CertRepMessage;
import org.bouncycastle.asn1.cmp.CertifiedKeyPair;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;
import org.bouncycastle.asn1.cmp.PollReqContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Handle inbound CMP {@code pollReq} messages (RFC 4210 §5.2.6) — the client's retry of an
 * earlier ir/cr/kur whose first response was a {@code pollRep} ("operation pending; come back
 * later"). The handler looks up the transaction by its {@code transactionID}, inspects the
 * associated certificate's state, and responds with one of:
 * <ul>
 *   <li><b>another {@code pollRep}</b> — when the certificate is still in
 *       {@code PENDING_ISSUE} or {@code PENDING_REVOKE} (the authority provider connector
 *       accepted the operation with HTTP 202 but completion is asynchronous);</li>
 *   <li><b>{@code ip}/{@code cp}/{@code kup}</b> with the issued certificate — when the
 *       certificate is now {@code ISSUED}. The response body type is reconstructed from the
 *       {@code originalRequestBodyType} stored on the transaction (so a client that sent ir
 *       gets ip back, cr → cp, kur → kup);</li>
 *   <li><b>error response</b> — when the operation failed or was rejected, or when no
 *       transaction exists for this {@code transactionID}.</li>
 * </ul>
 */
@Component
public class PollReqMessageHandler implements MessageHandler<PKIMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(PollReqMessageHandler.class);

    private static final long DEFAULT_CHECK_AFTER_SECONDS = 60L;

    private CmpTransactionService cmpTransactionService;

    @Autowired
    public void setCmpTransactionService(CmpTransactionService cmpTransactionService) {
        this.cmpTransactionService = cmpTransactionService;
    }

    @Override
    public PKIMessage handle(PKIMessage request, ConfigurationContext configuration) throws CmpBaseException {
        ASN1OctetString tid = request.getHeader().getTransactionID();
        if (request.getBody().getType() != PKIBody.TYPE_POLL_REQ) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "pollReq handler invoked for wrong body type=" + request.getBody().getType());
        }

        PollReqContent pollReqBody = (PollReqContent) request.getBody().getContent();
        BigInteger[] certReqIdValues = pollReqBody.getCertReqIdValues();
        if (certReqIdValues == null || certReqIdValues.length == 0) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "pollReq carries no certReqId entries");
        }

        // CZERTAINLY currently maps one transaction to one certificate, so any certReqId in the
        // pollReq body refers to the same in-flight operation. Use the first.
        ASN1Integer certReqId = new ASN1Integer(certReqIdValues[0]);

        List<CmpTransaction> transactions = cmpTransactionService.findByTransactionId(tid.toString());
        if (transactions.isEmpty()) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badRequest,
                    "no in-flight CMP transaction found for transactionId=" + tid);
        }
        CmpTransaction transaction = transactions.getFirst();
        Certificate certificate = transaction.getCertificate();
        if (certificate == null) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "CMP transaction has no associated certificate");
        }

        CertificateState state = certificate.getState();
        LOG.debug("TID={} | pollReq lookup: cert={}, state={}", tid, certificate.getUuid(), state);

        return switch (state) {
            case PENDING_ISSUE, PENDING_REVOKE ->
                    buildPollRep(request, configuration, certReqId,
                            "Awaiting asynchronous completion (state=" + state.getCode() + ")");
            case ISSUED ->
                    buildCertReadyResponse(request, configuration, tid, certReqId, transaction, certificate);
            case REVOKED, REJECTED, FAILED ->
                    throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                            "in-flight operation ended with state=" + state.getCode() + "; client should not poll");
            default ->
                    // REQUESTED, PENDING_APPROVAL — still in progress; keep the client polling.
                    buildPollRep(request, configuration, certReqId,
                            "Operation in progress (state=" + state.getCode() + ")");
        };
    }

    private PKIMessage buildPollRep(PKIMessage request, ConfigurationContext configuration,
                                    ASN1Integer certReqId, String reason) throws CmpBaseException {
        try {
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(PkiMessageBuilder.createPollRepBody(certReqId,
                            DEFAULT_CHECK_AFTER_SECONDS, reason))
                    .addExtraCerts(null)
                    .build();
        } catch (Exception e) {
            ASN1OctetString tid = request.getHeader().getTransactionID();
            LOG.error("TID={} | failed to build pollRep response", tid, e);
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "failed to build pollRep response", e);
        }
    }

    private PKIMessage buildCertReadyResponse(PKIMessage request, ConfigurationContext configuration,
                                              ASN1OctetString tid, ASN1Integer certReqId,
                                              CmpTransaction transaction, Certificate certificate)
            throws CmpBaseException {
        Integer originalBodyType = transaction.getOriginalRequestBodyType();
        // Default to cp (TYPE_CERT_REP) when the transaction was created before the body-type
        // column existed (NULL on legacy rows). Most cmp v2 clients accept cp as the response
        // for either ir or cr.
        int responseBodyType = (originalBodyType == null
                ? PKIBody.TYPE_CERT_REP
                : (originalBodyType + 1));

        X509Certificate x509;
        try {
            if (certificate.getCertificateContent() == null
                    || certificate.getCertificateContent().getContent() == null) {
                throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                        "certificate has no parseable content");
            }
            x509 = CertificateUtil.parseCertificate(certificate.getCertificateContent().getContent());
        } catch (CertificateException e) {
            LOG.error("TID={} | failed to parse stored certificate {}", tid, certificate.getUuid(), e);
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "failed to parse stored certificate: " + e.getMessage(), e);
        }

        try {
            CMPCertificate cmpCert = CMPCertificate.getInstance(x509.getEncoded());
            CertResponse certResponse = new CertResponse(
                    certReqId,
                    new PKIStatusInfo(PKIStatus.granted),
                    new CertifiedKeyPair(new CertOrEncCert(cmpCert)),
                    null);
            CertRepMessage repMessage = new CertRepMessage(null, new CertResponse[]{certResponse});
            PKIBody body = new PKIBody(responseBodyType, repMessage);
            return new PkiMessageBuilder(configuration)
                    .addHeader(PkiMessageBuilder.buildBasicHeaderTemplate(request))
                    .addBody(body)
                    .addExtraCerts(null)
                    .build();
        } catch (CmpBaseException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("TID={} | failed to build cert-ready pollReq response", tid, e);
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "failed to build cert-ready response: " + e.getMessage(), e);
        }
    }
}
