package com.czertainly.core.service;

import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.security.authz.SecuredUUID;

import java.util.List;

/**
 * Internal interface for Token Instance operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in TokenInstanceExternalService.
 */
public interface TokenInstanceInternalService extends ResourceExtensionService {

    /**
     * Get the token instance entity without authorization check. Internal use only.
     *
     * @param uuid UUID of the token instance
     * @return Token Instance entity
     * @throws NotFoundException when the token instance is not found
     */
    TokenInstanceReference getTokenInstanceEntity(SecuredUUID uuid) throws NotFoundException;

    /**
     * Validate the token profile attributes. Internal use only — no authorization check.
     *
     * @param uuid       UUID of the token instance
     * @param attributes attributes to be validated
     * @throws ConnectorException when there are issues with the communication
     * @throws NotFoundException  when the token instance is not found
     */
    void validateTokenProfileAttributes(SecuredUUID uuid, List<RequestAttribute> attributes) throws ConnectorException, NotFoundException;
}
