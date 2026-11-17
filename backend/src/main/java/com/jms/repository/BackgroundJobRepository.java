package com.jms.repository;

import com.jms.domain.enums.BackgroundJobStatus;
import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.BackgroundJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

    Optional<BackgroundJob> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT b FROM BackgroundJob b
            WHERE b.status IN :statuses
              AND b.nextRunAt <= :now
            ORDER BY b.priority DESC, b.createdAt ASC
            """)
    List<BackgroundJob> findDueJobs(@Param("statuses") Collection<BackgroundJobStatus> statuses,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    List<BackgroundJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<BackgroundJob> findByStatusOrderByCreatedAtDesc(BackgroundJobStatus status, Pageable pageable);

    long countByStatus(BackgroundJobStatus status);

    long countByType(BackgroundJobType type);

    @Query("SELECT AVG(b.executionDurationMs) FROM BackgroundJob b WHERE b.status = 'COMPLETED' AND b.executionDurationMs IS NOT NULL")
    Double findAverageExecutionDurationMs();
}
