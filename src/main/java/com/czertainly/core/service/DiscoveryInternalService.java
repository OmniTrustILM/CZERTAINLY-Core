package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.discovery.DiscoveryHistoryDetailDto;
import com.czertainly.core.tasks.ScheduledJobInfo;

import java.util.UUID;

/**
 * Internal interface for Discovery operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in DiscoveryExternalService.
 */
public interface DiscoveryInternalService extends ResourceExtensionService {

    DiscoveryHistoryDetailDto runDiscovery(UUID discoveryUuid, ScheduledJobInfo scheduledJobInfo);

}
