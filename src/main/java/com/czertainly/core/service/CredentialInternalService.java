package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.attribute.common.callback.AttributeCallback;
import com.czertainly.api.model.common.attribute.common.callback.RequestAttributeCallback;

/**
 * Internal interface for Credential operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in CredentialExternalService.
 */
public interface CredentialInternalService extends ResourceExtensionService {

    /**
     * Loads full credential data (secrets) for an attribute callback invocation.
     * Used internally during connector attribute callback processing; not directly
     * exposed to external callers.
     *
     * @param callback        the attribute callback descriptor
     * @param callbackRequest the callback request parameters
     * @throws NotFoundException when a referenced credential is not found
     */
    void loadFullCredentialData(AttributeCallback callback, RequestAttributeCallback callbackRequest) throws NotFoundException;
}
