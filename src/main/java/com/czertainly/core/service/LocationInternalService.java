package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.LocationException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.CertificateLocationId;

/**
 * Internal interface for Location operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in LocationExternalService.
 */
public interface LocationInternalService extends ResourceExtensionService {

    /**
     * Remove rejected new Certificate from location as result of async issue approval reject process.
     *
     * @param certificateLocationId ID of CertificateLocation entity
     * @throws NotFoundException when the CertificateLocation with the given Id is not found.
     */
    void removeRejectedOrFailedCertificateFromLocationAction(CertificateLocationId certificateLocationId) throws ConnectorException, NotFoundException;

    /**
     * Push existing requested Certificate to the given Location as result of async issue process.
     *
     * @param certificateLocationId ID of CertificateLocation entity
     * @param isRenewal             indication if certificate to be pushed was renewed
     * @throws NotFoundException when the CertificateLocation with the given Id is not found.
     * @throws LocationException when the Certificate failed to be pushed to the Location.
     */
    void pushRequestedCertificateToLocationAction(CertificateLocationId certificateLocationId, boolean isRenewal) throws NotFoundException, LocationException, AttributeException;

}
