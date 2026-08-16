package com.example.workitems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.test.context.jdbc.Sql;

@DataJdbcTest(properties = "spring.sql.init.mode=never")
@Sql(scripts = "/schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class WorkItemRepositoryTest {

    @Autowired
    private WorkItemRepository repository;

    @Test
    void save_inserts_a_new_work_item_even_when_its_uuid_is_assigned_by_the_application() {
        WorkItem workItem = WorkItem.create("再現用の作業項目");

        assertThatCode(() -> repository.save(workItem))
                .doesNotThrowAnyException();

        assertThat(repository.findById(workItem.id()))
                .isPresent()
                .get()
                .extracting(WorkItem::title)
                .isEqualTo("再現用の作業項目");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void save_updates_an_aggregate_reloaded_after_its_initial_insert() {
        WorkItem created = WorkItem.create("初期タイトル");
        repository.save(created);

        WorkItem reloaded = repository.findById(created.id()).orElseThrow();
        assertThatCode(() -> repository.save(reloaded.rename("更新後タイトル")))
                .doesNotThrowAnyException();

        assertThat(repository.findById(created.id()))
                .isPresent()
                .get()
                .extracting(WorkItem::title)
                .isEqualTo("更新後タイトル");
        assertThat(repository.count()).isEqualTo(1);
    }
}
