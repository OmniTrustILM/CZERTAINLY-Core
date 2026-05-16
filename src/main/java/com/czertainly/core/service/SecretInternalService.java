package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.core.messaging.model.ActionMessage;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.UUID;

/**
 * Internal interface for Secret operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in SecretExternalService.
 */
public interface SecretInternalService extends ResourceExtensionService {

    /**
     * Invoked after an approval is created for a Secret action.
     * Sets the Secret state to reflect the pending approval.
     *
     * @param resourceUuid UUID of the Secret resource
     * @throws NotFoundException when the Secret is not found
     */
    void approvalCreatedAction(UUID resourceUuid) throws NotFoundException;

    /**
     * Processes a Secret action message after it has been approved or rejected.
     *
     * @param actionMessage the action message describing the operation
     * @param hasApproval   whether the action required approval
     * @param isApproved    whether the action was approved
     */
    void processSecretAction(ActionMessage actionMessage, boolean hasApproval, boolean isApproved) throws ConnectorException, NotFoundException, AttributeException, JsonProcessingException, SecretOperationException;
}
