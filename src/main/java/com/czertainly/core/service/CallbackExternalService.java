package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.common.callback.RequestAttributeCallback;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.connector.FunctionGroupCode;

import java.util.UUID;

public interface CallbackExternalService {

    Object callback(
            String uuid,
            FunctionGroupCode functionGroup,
            String kind,
            RequestAttributeCallback callback
    ) throws ConnectorException, ValidationException, NotFoundException, AttributeException;

    Object callback(UUID uuid, RequestAttributeCallback callback) throws NotFoundException, ConnectorException, AttributeException;

    Object resourceCallback(
            Resource resource,
            String resourceUuid,
            RequestAttributeCallback callback
    ) throws ConnectorException, ValidationException, NotFoundException, AttributeException;
}
