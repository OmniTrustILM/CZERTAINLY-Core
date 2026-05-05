package com.czertainly.core.service.cmp.message.handler;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.interfaces.core.cmp.error.CmpProcessingException;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.service.CertificateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PollFeature {

    private static final Logger LOG = LoggerFactory.getLogger(PollFeature.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${cmp.protocol.poll.feature.timeout}")
    private Integer pollFeatureTimeout;

    private CertificateService certificateService;

    @Autowired
    public void setCertificateService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    /**
     * Convert asynchronous behaviour (manipulation with certificate, e.g. issuing/re-keying/revoking) to synchronous
     * (cmp client ask for certificate) using polling certificate until certificate.
     *
     * <p>If the certificate is in a non-synchronous {@code PENDING_ISSUE} or {@code PENDING_REVOKE}
     * state (e.g. the connector returned {@code 202 Accepted} for offline-RA flows), this method
     * returns {@code null} immediately instead of waiting for a state change that will not happen
     * within the poll-timeout window. The caller is expected to translate this into a CMP
     * {@code pollRep} response so the client knows to retry later.</p>
     *
     * @param tid          processing transaction id, see {@link PKIHeader#getTransactionID()}
     * @param serialNumber of given certificate subject of polling
     * @return polled certificate when it reaches {@code expectedState}; {@code null} when the
     *         certificate is in a non-synchronous PENDING state
     * @throws CmpProcessingException if polling of certificate failed
     */
    public Certificate pollCertificate(ASN1OctetString tid, String serialNumber, String uuid, CertificateState expectedState)
            throws CmpProcessingException {
        LOG.trace(">>>>> CERT POLL (begin) >>>>> ");
        SecuredUUID certUUID = SecuredUUID.fromString(uuid);

        Certificate polledCert;
        try {
            LOG.trace("TID={}, SN={} | Polling of certificate with uuid={}", tid, serialNumber, certUUID);
            long startRequest = System.currentTimeMillis();
            long endRequest;
            int cfgValue = pollFeatureTimeout == null ? 10 : pollFeatureTimeout;//in seconds
            int timeout = 1000 * cfgValue;
            int counter = 0;//counter for logging purpose only
            boolean isPendingNonSynchronous = false;
            do {
                LOG.trace(">>>>> TID={}, POLL=[{}] SN={} | polling request: certificate with uuid={}",
                        tid, counter, serialNumber, certUUID);
                // -- (2)certification polling (ask for created certificate entity)
                polledCert = certificateService.getCertificateEntity(certUUID);
                LOG.trace("<<<<< TID={}, POLL=[{}] SN={} | polling result: certificate entity in state {}, uuid={}",
                        tid, counter, polledCert.getSerialNumber(), polledCert.getState(), certUUID);
                endRequest = System.currentTimeMillis();
                counter++;
                entityManager.refresh(polledCert);//get entity from db (instead from hibernate 1lvl cache)
                // Non-synchronous (offline-RA) signal: if the certificate has reached a PENDING_*
                // state, the connector returned 202 Accepted and the operation will complete
                // externally. Stop polling and signal the caller to return a CMP pollRep so the
                // client knows to retry later. Captured here (inside the loop) to also catch the
                // realistic case where the transition happens during the wait window, not only
                // when the cert is already PENDING_* at the start.
                if (polledCert.getState() == CertificateState.PENDING_ISSUE
                        || polledCert.getState() == CertificateState.PENDING_REVOKE) {
                    isPendingNonSynchronous = true;
                    break;
                }
                if (counter > 1) TimeUnit.MILLISECONDS.sleep(1000);
                if (serialNumber == null) serialNumber = polledCert.getSerialNumber();
            } while ((endRequest - startRequest < timeout)
                    && !expectedState.equals(polledCert.getState()));
            if (isPendingNonSynchronous) {
                LOG.trace("TID={}, SN={} | certificate uuid={} reached non-synchronous {} state — caller will return pollRep",
                        tid, serialNumber, certUUID, polledCert.getState());
                return null;
            }
            LOG.trace("TID={}, SN={} | Polling of certificate with uuid={} is done", tid, serialNumber, certUUID);
        } catch (InterruptedException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    "SN=" + serialNumber + " | cannot poll certificate - processing thread has been interrupted", e);
        } catch (NotFoundException e) {
            throw new CmpProcessingException(tid, PKIFailureInfo.badDataFormat,
                    "SN=" + serialNumber + " | issued certificate from CA cannot be found, uuid=" + certUUID);
        } finally {
            LOG.trace("<<<<< CERT polling (  end) <<<<< ");
        }

        if (!expectedState.equals(polledCert.getState())) {
            throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
                    String.format("SN=%s | polled certificate is not at valid state (expected=%s), retrieved=%s",
                            serialNumber,
                            expectedState,
                            polledCert.getState()));
        }
        return polledCert;
    }

}
