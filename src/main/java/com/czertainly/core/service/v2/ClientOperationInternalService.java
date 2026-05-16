package com.czertainly.core.service.v2;

import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.v2.ClientCertificateRekeyRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;

import java.util.UUID;

/**
 * Internal interface for certificate operation actions triggered by approval or messaging flows.
 * These methods are not directly accessible via API controllers; controller-callable methods
 * are defined in ClientOperationExternalService.
 */
public interface ClientOperationInternalService {

    void approvalCreatedAction(UUID certificateUuid) throws NotFoundException;

    void issueCertificateAction(UUID certificateUuid, boolean isApproved) throws com.czertainly.api.exception.ConnectorException, java.security.cert.CertificateException, java.security.NoSuchAlgorithmException, com.czertainly.api.exception.AlreadyExistException, CertificateOperationException, NotFoundException;

    void issueCertificateRejectedAction(UUID certificateUuid) throws NotFoundException;

    void renewCertificateAction(UUID certificateUuid, ClientCertificateRenewRequestDto request, boolean isApproved) throws NotFoundException, CertificateOperationException;

    void rekeyCertificateAction(UUID certificateUuid, ClientCertificateRekeyRequestDto request, boolean isApproved) throws NotFoundException, CertificateOperationException;

    void revokeCertificateAction(UUID certificateUuid, ClientCertificateRevocationDto request, boolean isApproved) throws NotFoundException, CertificateOperationException;
}
