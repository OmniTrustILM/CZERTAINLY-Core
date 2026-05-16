package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.compliance.v2.ComplianceCheckResultDto;
import com.czertainly.core.model.compliance.ComplianceResultDto;

import java.util.List;
import java.util.UUID;

public interface ComplianceInternalService {

    /**
     * Get the latest compliance check result for the specified resource object using the provided compliance result
     *
     * @param resource Resource of the object
     * @param objectUuid UUID of the object
     * @param complianceResult ComplianceResultDto containing the compliance check result data
     * @return ComplianceCheckResultDto containing the result of the latest compliance check
     */
    ComplianceCheckResultDto getComplianceCheckResult(Resource resource, UUID objectUuid, ComplianceResultDto complianceResult);

    /**
     * Check compliance on specified resource object as system user (no user context).
     * Warning: This method should be used only when running compliance check as part of system operations since it bypasses permissions.
     *
     * @param resource Resource of objects checked by compliance
     * @param objectUuid UUID of object to be checked
     */
    void checkResourceObjectComplianceAsSystem(Resource resource, UUID objectUuid);

    /**
     * Trigger compliance validation for a set of resource objects without requiring COMPLIANCE_PROFILE permission.
     * Intended for internal service-to-service calls where the caller already holds appropriate authority
     * (e.g. from the certificate issuance flow inside ClientOperationService).
     * Does not enforce a separate compliance-profile permission gate.
     *
     * @param resource    Resource type of the objects to check
     * @param objectUuids UUIDs of the objects to check compliance for
     */
    void checkResourceObjectsComplianceValidationInternal(Resource resource, List<UUID> objectUuids) throws NotFoundException;
}
