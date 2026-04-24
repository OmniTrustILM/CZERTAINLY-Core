package com.czertainly.core.service.impl;

import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.workflows.ObjectEventHistoryDto;
import com.czertainly.core.service.EventService;
import com.czertainly.core.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EventServiceImpl implements EventService {

    private ResourceService resourceService;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }


    @Override
    public PaginationResponseDto<ObjectEventHistoryDto> getEventHistory(Resource resource, UUID uuid, PaginationRequestDto pagination) {
        resourceService.evaluateDetailsPermission(resource, uuid);

        return null;
    }
}
