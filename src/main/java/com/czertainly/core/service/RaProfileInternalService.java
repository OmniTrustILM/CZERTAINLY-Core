package com.czertainly.core.service;

import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.service.model.SecuredList;

import java.util.List;

/**
 * Internal interface for RA Profile operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in RaProfileExternalService.
 */
public interface RaProfileInternalService extends ResourceExtensionService {

    /**
     * Save the RA Profile entity to the database.
     *
     * @param raProfile RA profile entity
     * @return persisted RA Profile entity
     */
    RaProfile updateRaProfileEntity(RaProfile raProfile);

    /**
     * Remove SCEP profile associations from a list of RA Profiles.
     *
     * @param uuids list of RA Profile UUIDs
     */
    void bulkRemoveAssociatedScepProfile(List<SecuredUUID> uuids);

    /**
     * Remove CMP profile associations from a list of RA Profiles.
     *
     * @param uuids list of RA Profile UUIDs
     */
    void bulkRemoveAssociatedCmpProfile(List<SecuredUUID> uuids);

    /**
     * List all RA Profiles associated with a given SCEP Profile.
     *
     * @param scepProfileUuid UUID of the SCEP Profile
     * @param filter          security filter
     * @return secured list of RA Profiles
     */
    SecuredList<RaProfile> listRaProfilesAssociatedWithScepProfile(String scepProfileUuid, SecurityFilter filter);

}
