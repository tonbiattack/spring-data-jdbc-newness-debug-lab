package com.example.workitems;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("work_item")
public class WorkItem {

    @Id
    private final UUID id;

    private final String title;

    private WorkItem(UUID id, String title) {
        this.id = id;
        this.title = title;
    }

    public static WorkItem create(String title) {
        return new WorkItem(UUID.randomUUID(), title);
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }
}
