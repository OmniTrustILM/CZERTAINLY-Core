package com.czertainly.core.service.impl;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.api.model.core.workflows.ObjectEventHistoryDto;
import com.czertainly.api.model.core.workflows.TriggeredEventOriginDto;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.entity.workflows.TriggerHistoryRecord;
import com.czertainly.core.dao.repository.workflows.TriggerHistoryRepository;
import com.czertainly.core.service.EventService;
import com.czertainly.core.service.ResourceService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EventServiceImpl implements EventService {

    private ResourceService resourceService;

    private TriggerHistoryRepository triggerHistoryRepository;

    @Autowired
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Autowired
    public void setTriggerHistoryRepository(TriggerHistoryRepository triggerHistoryRepository) {
        this.triggerHistoryRepository = triggerHistoryRepository;
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

    private static @NonNull TriggeredEventOriginDto getTriggeredEventOriginDto(TriggerHistory triggerHistory) {
        TriggeredEventOriginDto triggeredEventOriginDto = new TriggeredEventOriginDto();
        triggeredEventOriginDto.setType(triggerHistory.getTriggerAssociation().getResource() == null ? Resource.SETTINGS : triggerHistory.getTriggerAssociation().getResource());
        if (triggerHistory.getTriggerAssociation().getObjectUuid() != null) triggeredEventOriginDto.setResource(new NameAndUuidDto(triggerHistory.getTriggerAssociation().getObjectUuid(), null));
        return triggeredEventOriginDto;
    }
}
