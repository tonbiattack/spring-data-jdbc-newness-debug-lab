package com.example.workitems;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("work_item")
public class WorkItem {

    @Id
    private final UUID id;

    private final String title;

    @Version
    private final Long version;

    private WorkItem(UUID id, String title, Long version) {
        this.id = id;
        this.title = title;
        this.version = version;
    }

    public static WorkItem create(String title) {
        return new WorkItem(UUID.randomUUID(), title, null);
    }

    public WorkItem rename(String newTitle) {
        return new WorkItem(id, newTitle, version);
    }

    public UUID id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Long version() {
        return version;
    }
}
