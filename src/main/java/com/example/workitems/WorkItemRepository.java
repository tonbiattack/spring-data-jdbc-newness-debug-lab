package com.example.workitems;

import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

public interface WorkItemRepository extends CrudRepository<WorkItem, UUID> {
}
