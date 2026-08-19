package com.wakebook.book.service;

import com.wakebook.book.domain.HiddenBookSource;
import com.wakebook.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-test")
class HiddenBookJobMySqlConcurrencyTest {

    private static final String LIBRARY_CODE = "LOCK-TEST-121020";

    @Autowired
    private HiddenBookJobService jobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanPreviousAttempt() {
        jdbcTemplate.update("DELETE FROM hidden_book_jobs WHERE library_code = ?", LIBRARY_CODE);
        jdbcTemplate.update("DELETE FROM hidden_book_collection_locks WHERE library_code = ?", LIBRARY_CODE);
    }

    @Test
    void 빈_MySQL에_잠금_테이블_마이그레이션이_적용된다() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }

        Integer tableCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'hidden_book_collection_locks'
            """, Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE script = 'V202608150005__create_hidden_book_collection_locks.sql'
              AND success = 1
            """, Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isEqualTo(1);
    }

    @RepeatedTest(10)
    void 같은_도서관의_동시_요청은_하나만_생성한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<CreateOutcome> first = createAsync(executor, ready, start, 101L);
            CompletableFuture<CreateOutcome> second = createAsync(executor, ready, start, 202L);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CreateOutcome> outcomes = List.of(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS)
            );

            assertThat(outcomes).filteredOn(CreateOutcome::created).hasSize(1);
            assertThat(outcomes)
                .filteredOn(outcome -> "JOB_001".equals(outcome.errorCode()))
                .hasSize(1);
            assertThat(countJobs()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private CompletableFuture<CreateOutcome> createAsync(
        ExecutorService executor,
        CountDownLatch ready,
        CountDownLatch start,
        Long requestedBy
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            try {
                jobService.create(
                    LIBRARY_CODE,
                    "동시성 테스트 도서관",
                    HiddenBookSource.LIBRARY_API,
                    requestedBy,
                    false
                );
                return new CreateOutcome(true, null);
            } catch (ApiException exception) {
                return new CreateOutcome(false, exception.getCode());
            }
        }, executor);
    }

    private long countJobs() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM hidden_book_jobs WHERE library_code = ?",
            Long.class,
            LIBRARY_CODE
        );
        return count == null ? 0 : count;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 요청 시작 신호를 기다리지 못했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 요청 대기가 중단되었습니다.", exception);
        }
    }

    private record CreateOutcome(boolean created, String errorCode) {
    }
}
