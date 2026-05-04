package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.other.ResourceEvent;
import com.czertainly.api.model.core.other.ResourceObjectDto;
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
import jakarta.transaction.Transactional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
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
                            if (triggerHistory.getTriggerAssociation().getResource() != null) {
                                try {
                                    dto.setOrigin(resourceService.getResourceObject(triggerHistory.getTriggerAssociation().getResource(), triggerHistory.getTriggerAssociation().getUuid()));
                                } catch (NotFoundException e) {
                                    dto.setOrigin(new ResourceObjectDto(triggerHistory.getTriggerAssociation().getResource(), null, null));
                                }
                            }
                            if (triggerHistory.getTrigger() != null) {
                                dto.setTrigger(new NameAndUuidDto(triggerHistory.getTriggerUuid(), triggerHistory.getTrigger().getName()));
                            }
                            dto.setConditionsMatched(triggerHistory.isConditionsMatched());
                            dto.setActionsPerformed(triggerHistory.isActionsPerformed());
                            dto.setTriggeredAt(triggerHistory.getTriggeredAt());
                            dto.setMessage(triggerHistory.getMessage());
                            dto.setRecords(triggerHistory.getRecords().stream().map(TriggerHistoryRecord::mapToDto).toList());
                            dto.setNotificationsSent(notificationsSent(triggerHistory));
                            return dto;
                        }
                ).toList()
        );

        return response;
    }

    @Nullable
    private static Boolean notificationsSent(TriggerHistory triggerHistory) {
        // If there was any action sending notifications and conditions were met and there is no trigger history record for failed execution with send notification type
        boolean notificationsInTrigger = triggerHistory.getTrigger().getActions().stream()
                .anyMatch(action -> action.getExecutions().stream().anyMatch(e -> e.getType() == ExecutionType.SEND_NOTIFICATION));
        if (notificationsInTrigger) {
            return
                    triggerHistory.isConditionsMatched() &&
                            triggerHistory.getRecords().stream().noneMatch(r -> r.getExecution() != null && r.getExecution().getType() == ExecutionType.SEND_NOTIFICATION);
        }
        return null;
    }

    @Override
    @ExternalAuthorization(resource = Resource.RESOURCE_EVENT, action = ResourceAction.DETAIL)
    public PaginationResponseDto<EventHistoryDto> getEventHistory(ResourceEvent event, Resource resource, UUID uuid, EventHistoryRequestDto request) throws NotFoundException {
        if (uuid == null && resource != null || uuid != null && resource == null) {
            throw new ValidationException("Missing UUID or Resource");
        }
        if (uuid != null) {
            resourceService.evaluateDetailsPermission(resource, uuid);
        }

        Page<EventHistory> eventHistories = eventHistoryRepository.findByEventAndResourceAndResourceUuidOrderByStartedAtDesc(event, resource, uuid, Pageable.ofSize(request.getPagination().getItemsPerPage()).withPage(request.getPagination().getPageNumber() - 1));
        List<EventHistoryDto> eventHistoriesResponse = new ArrayList<>();
        for (EventHistory eventHistory : eventHistories) {
            EventHistoryDto dto = new EventHistoryDto();
            dto.setStartedAt(eventHistory.getStartedAt());
            dto.setFinishedAt(eventHistory.getFinishedAt());
            dto.setStatus(eventHistory.getStatus());
            dto.setObjectsEvaluated(triggerHistoryRepository.countDistinctObjectUuidByEventHistoryUuid(eventHistory.getUuid()));
            dto.setObjectsMatched(triggerHistoryRepository.countDistinctObjectUuidByEventHistoryUuidAndConditionsMatchedTrue(eventHistory.getUuid()));
            dto.setObjectsIgnored(triggerHistoryRepository.countDistinctObjectUuidByEventHistoryUuidAndConditionsMatchedTrueAndTriggerIgnoreTriggerTrue(eventHistory.getUuid()));
            Page<UUID> objectUuids = triggerHistoryRepository.findDistinctObjectUuidsByEventHistoryUuid(eventHistory.getUuid(), Pageable.ofSize(request.getObjectsPagination().getItemsPerPage()).withPage(request.getObjectsPagination().getPageNumber() - 1));
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
                    triggerHistoryDto.setNotificationsSent(notificationsSent(triggerHistory));
                    return triggerHistoryDto;
                }).toList());
                triggerHistoriesInEvent.add(triggerHistoryObjectSummaryDto);
            }

            PaginationResponseDto<TriggerHistoryObjectSummaryDto> triggerHistoriesPaginated = new PaginationResponseDto<>();
            triggerHistoriesPaginated.setItemsPerPage(request.getObjectsPagination().getItemsPerPage());
            triggerHistoriesPaginated.setPageNumber(request.getObjectsPagination().getPageNumber());
            triggerHistoriesPaginated.setItems(triggerHistoriesInEvent);
            triggerHistoriesPaginated.setTotalItems(dto.getObjectsEvaluated());
            dto.setObjectHistories(triggerHistoriesPaginated);
            eventHistoriesResponse.add(dto);
        }

        return getEventHistoryDtoPaginationResponseDto(request.getPagination(), eventHistoriesResponse, eventHistories);
    }

    private static @NonNull PaginationResponseDto<EventHistoryDto> getEventHistoryDtoPaginationResponseDto(PaginationRequestDto pagination, List<EventHistoryDto> eventHistoriesResponse, Page<EventHistory> eventHistories) {
        PaginationResponseDto<EventHistoryDto> eventHistoriesPaginatedResponse = new PaginationResponseDto<>();
        eventHistoriesPaginatedResponse.setItems(eventHistoriesResponse);
        eventHistoriesPaginatedResponse.setTotalItems(eventHistories.getTotalElements());
        eventHistoriesPaginatedResponse.setTotalPages(eventHistories.getTotalPages());
        eventHistoriesPaginatedResponse.setPageNumber(pagination.getPageNumber());
        eventHistoriesPaginatedResponse.setItemsPerPage(pagination.getItemsPerPage());
        return eventHistoriesPaginatedResponse;
    }
}
