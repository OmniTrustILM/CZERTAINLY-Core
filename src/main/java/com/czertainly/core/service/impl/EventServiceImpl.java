package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.NestedPaginationRequestDto;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.workflows.*;
import com.czertainly.core.dao.entity.workflows.EventHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistoryRecord;
import com.czertainly.core.dao.repository.workflows.EventHistoryRepository;
import com.czertainly.core.dao.repository.workflows.TriggerHistoryRepository;
import com.czertainly.core.model.auth.ResourceAction;
import com.czertainly.core.security.authz.ExternalAuthorization;
import com.czertainly.core.service.EventService;
import com.czertainly.core.service.ResourceService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EventServiceImpl implements EventService {

    private ResourceService resourceService;

    private TriggerHistoryRepository triggerHistoryRepository;
    private EventHistoryRepository eventHistoryRepository;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Autowired
    public void setTriggerHistoryRepository(TriggerHistoryRepository triggerHistoryRepository) {
        this.triggerHistoryRepository = triggerHistoryRepository;
    }

    @Autowired
    public void setEventHistoryRepository(EventHistoryRepository eventHistoryRepository) {
        this.eventHistoryRepository = eventHistoryRepository;
    }


    @Override
    public PaginationResponseDto<ObjectEventHistoryDto> getEventHistory(Resource resource, UUID uuid, PaginationRequestDto pagination) throws NotFoundException {
        resourceService.evaluateDetailsPermission(resource, uuid);
        Page<TriggerHistory> triggerHistoryPage = triggerHistoryRepository.findByObjectUuidAndTriggerResourceOrderByTriggeredAtDesc(uuid, resource, Pageable.ofSize(pagination.getItemsPerPage()).withPage(pagination.getPageNumber()));
        PaginationResponseDto<ObjectEventHistoryDto> response = new PaginationResponseDto<>();
        response.setItemsPerPage(pagination.getItemsPerPage());
        response.setPageNumber(pagination.getPageNumber());
        response.setTotalItems(triggerHistoryPage.getTotalElements());
        response.setTotalPages((int) Math.ceil((double) triggerHistoryPage.getTotalElements() / pagination.getItemsPerPage()));
        response.setItems(
                triggerHistoryPage.get().map(
                        triggerHistory -> {
                            ObjectEventHistoryDto dto = new ObjectEventHistoryDto();
                            dto.setEvent(triggerHistory.getEvent());
                            if (triggerHistory.getTriggerAssociation() != null) {
                                TriggeredEventOriginDto triggeredEventOriginDto = getTriggeredEventOriginDto(triggerHistory);
                                dto.setTriggeredEventOrigin(triggeredEventOriginDto);
                            }
                            if (triggerHistory.getTrigger() != null) {
                                dto.setTrigger(new NameAndUuidDto(triggerHistory.getTriggerUuid(), triggerHistory.getTrigger().getName()));
                            }
                            dto.setConditionsMatched(triggerHistory.isConditionsMatched());
                            dto.setActionsPerformed(triggerHistory.isActionsPerformed());
                            dto.setTriggeredAt(triggerHistory.getTriggeredAt());
                            dto.setMessage(triggerHistory.getMessage());
                            dto.setRecords(triggerHistory.getRecords().stream().map(TriggerHistoryRecord::mapToDto).toList());
                            return dto;
                        }
                ).toList()
        );

        return response;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RESOURCE_EVENT, action = ResourceAction.DETAIL)
    public PaginationResponseDto<EventHistoryDto> getEventHistory(ResourceEvent event, Resource resource, UUID uuid, NestedPaginationRequestDto pagination) throws NotFoundException {
        if (uuid == null && resource != null || uuid != null && resource == null) {
            throw new ValidationException("Missing UUID or Resource");
        }
        if (uuid != null) {
            resourceService.evaluateDetailsPermission(resource, uuid);
        }

        Page<EventHistory> eventHistories = eventHistoryRepository.findByEventAndResourceAndResourceUuid(event, resource, uuid, Pageable.ofSize(pagination.getItemsPerPage()).withPage(pagination.getPageNumber() - 1));
        List<EventHistoryDto> eventHistoriesResponse = new ArrayList<>();
        for (EventHistory eventHistory : eventHistories) {
            EventHistoryDto dto = new EventHistoryDto();
            dto.setStartedAt(eventHistory.getStartedAt());
            dto.setFinishedAt(eventHistory.getFinishedAt());
            dto.setStatus(eventHistory.getStatus());
            dto.setObjectsEvaluated(triggerHistoryRepository.countDistinctObjectUuidByEventHistoryUuid(eventHistory.getUuid()));
            dto.setObjectsMatched(triggerHistoryRepository.countDistinctObjectUuidByEventHistoryUuidAndConditionsMatchedTrue(eventHistory.getUuid()));
            dto.setObjectsIgnored(triggerHistoryRepository.countDistinctObjectUuidByEventHistoryUuidAndConditionsMatchedTrueAndTriggerIgnoreTriggerTrue(eventHistory.getUuid()));
            Page<UUID> objectUuids = triggerHistoryRepository.findDistinctObjectUuidsByEventHistoryUuid(eventHistory.getUuid(), Pageable.ofSize(pagination.getInnerItemsPerPage()).withPage(pagination.getInnerPageNumber() - 1));
            List<TriggerHistoryObjectSummaryDto> triggerHistoriesInEvent = new ArrayList<>();
            for (UUID objectUuid : objectUuids) {
                List<TriggerHistory> triggerHistories = triggerHistoryRepository.findByEventHistoryUuidAndObjectUuidOrderByObjectUuidAscTriggeredAtDesc(eventHistory.getUuid(), objectUuid);
                TriggerHistoryObjectSummaryDto triggerHistoryObjectSummaryDto = new TriggerHistoryObjectSummaryDto();
                triggerHistoryObjectSummaryDto.setObjectUuid(objectUuid);
                triggerHistoryObjectSummaryDto.setTriggers(triggerHistories.stream().map(triggerHistory -> {
                    TriggerHistoryObjectTriggerSummaryDto triggerHistoryDto = new TriggerHistoryObjectTriggerSummaryDto();
                    triggerHistoryDto.setMessage(triggerHistory.getMessage());
                    triggerHistoryDto.setTriggerName(triggerHistory.getTrigger() != null ? triggerHistory.getTrigger().getName() : null);
                    triggerHistoryDto.setTriggerUuid(triggerHistory.getTriggerUuid());
                    triggerHistoryDto.setTriggeredAt(triggerHistory.getTriggeredAt());
                    triggerHistoryDto.setRecords(triggerHistory.getRecords().stream().map(TriggerHistoryRecord::mapToDto).toList());
                    return triggerHistoryDto;
                }).toList());
                triggerHistoriesInEvent.add(triggerHistoryObjectSummaryDto);
            }

            PaginationResponseDto<TriggerHistoryObjectSummaryDto> triggerHistoriesPaginated = new PaginationResponseDto<>();
            triggerHistoriesPaginated.setItemsPerPage(pagination.getInnerItemsPerPage());
            triggerHistoriesPaginated.setPageNumber(pagination.getInnerPageNumber());
            triggerHistoriesPaginated.setItems(triggerHistoriesInEvent);
            triggerHistoriesPaginated.setTotalItems(dto.getObjectsEvaluated());
            dto.setObjectHistories(triggerHistoriesPaginated);
            eventHistoriesResponse.add(dto);
        }

        return getEventHistoryDtoPaginationResponseDto(pagination, eventHistoriesResponse, eventHistories);
    }

    private static @NonNull PaginationResponseDto<EventHistoryDto> getEventHistoryDtoPaginationResponseDto(NestedPaginationRequestDto pagination, List<EventHistoryDto> eventHistoriesResponse, Page<EventHistory> eventHistories) {
        PaginationResponseDto<EventHistoryDto> eventHistoriesPaginatedResponse = new PaginationResponseDto<>();
        eventHistoriesPaginatedResponse.setItems(eventHistoriesResponse);
        eventHistoriesPaginatedResponse.setTotalItems(eventHistories.getTotalElements());
        eventHistoriesPaginatedResponse.setTotalPages(eventHistories.getTotalPages());
        eventHistoriesPaginatedResponse.setPageNumber(pagination.getPageNumber());
        eventHistoriesPaginatedResponse.setItemsPerPage(pagination.getItemsPerPage());
        return eventHistoriesPaginatedResponse;
    }

    private static @NonNull TriggeredEventOriginDto getTriggeredEventOriginDto(TriggerHistory triggerHistory) {
        TriggeredEventOriginDto triggeredEventOriginDto = new TriggeredEventOriginDto();
        triggeredEventOriginDto.setType(triggerHistory.getTriggerAssociation().getResource() == null ? Resource.SETTINGS : triggerHistory.getTriggerAssociation().getResource());
        if (triggerHistory.getTriggerAssociation().getObjectUuid() != null)
            triggeredEventOriginDto.setResource(new NameAndUuidDto(triggerHistory.getTriggerAssociation().getObjectUuid(), null));
        return triggeredEventOriginDto;
    }
}
