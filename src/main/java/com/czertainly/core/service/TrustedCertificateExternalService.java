package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.trustedcertificate.TrustedCertificateDto;
import com.czertainly.api.model.client.trustedcertificate.TrustedCertificateRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;

public interface TrustedCertificateExternalService {

    List<TrustedCertificateDto> listTrustedCertificates();

    TrustedCertificateDto getTrustedCertificate(SecuredUUID uuid) throws NotFoundException;

    TrustedCertificateDto createTrustedCertificate(TrustedCertificateRequestDto request);

    void deleteTrustedCertificate(SecuredUUID uuid) throws NotFoundException;
}
