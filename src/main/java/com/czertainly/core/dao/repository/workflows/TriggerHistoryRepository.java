package com.czertainly.core.dao.repository.workflows;


import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.workflows.TriggerHistory;
import com.czertainly.core.dao.repository.SecurityFilterRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Repository
public interface TriggerHistoryRepository extends SecurityFilterRepository<TriggerHistory, UUID> {

    @EntityGraph(attributePaths = {"records"})
    List<TriggerHistory> findAllByTriggerUuidAndTriggerAssociationObjectUuid(UUID triggerUuid, UUID triggerAssociationObjectUuid);

    @EntityGraph(attributePaths = {"records"})
    List<TriggerHistory> findByTriggerAssociationObjectUuidOrderByTriggerUuidAscTriggeredAtAsc(UUID triggerAssociationObjectUuid);

    Long deleteByTriggerAssociationObjectUuid(UUID triggerAssociationObjectUuid);

    @EntityGraph(attributePaths = {"records", "triggerAssociation", "records.execution", "records.execution.items", "records.execution.items.notificationProfile"})
    Page<TriggerHistory> findByObjectUuidAndObjectResourceOrderByTriggeredAtDesc(UUID objectUuid, Resource objectResource, Pageable pageable);

    @Query(value = "SELECT DISTINCT t.objectUuid FROM TriggerHistory t WHERE t.eventHistoryUuid = :uuid",
           countQuery = "SELECT COUNT(DISTINCT t.objectUuid) FROM TriggerHistory t WHERE t.eventHistoryUuid = :uuid")
    Page<UUID> findDistinctObjectUuidsByEventHistoryUuid(@Param("uuid") UUID uuid, Pageable pageable);

    @EntityGraph(attributePaths = {"records", "triggerAssociation", "records.execution", "records.execution.items", "records.execution.items.notificationProfile"})
    List<TriggerHistory> findByEventHistoryUuidAndObjectUuidInOrderByObjectUuidAscTriggeredAtDesc(UUID eventHistoryUuid, List<UUID> objectUuids);

    @Query("SELECT COUNT(DISTINCT t.objectUuid) FROM TriggerHistory t WHERE t.eventHistoryUuid = :uuid")
    int countDistinctObjectUuidByEventHistoryUuid(@Param("uuid") UUID uuid);

    @Query("SELECT COUNT(DISTINCT t.objectUuid) FROM TriggerHistory t WHERE t.eventHistoryUuid = :uuid AND t.conditionsMatched = true")
    int countDistinctObjectUuidByEventHistoryUuidAndConditionsMatchedTrue(@Param("uuid") UUID uuid);

    @Query("SELECT COUNT(DISTINCT t.objectUuid) FROM TriggerHistory t WHERE t.eventHistoryUuid = :uuid AND t.conditionsMatched = true AND t.trigger.ignoreTrigger = true")
    int countDistinctObjectUuidByEventHistoryUuidAndConditionsMatchedTrueAndTriggerIgnoreTriggerTrue(@Param("uuid") UUID uuid);

}
