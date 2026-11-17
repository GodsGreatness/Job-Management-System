package com.jms.repository;

import com.jms.domain.enums.EmploymentType;
import com.jms.domain.enums.JobStatus;
import com.jms.domain.model.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    List<JobPosting> findByEmployerIdOrderByCreatedAtDesc(Long employerId);

    List<JobPosting> findByStatusOrderByCreatedAtDesc(JobStatus status);

    long countByStatus(JobStatus status);

    @Query("""
            SELECT j FROM JobPosting j
            WHERE (:status IS NULL OR j.status = :status)
              AND (:remote IS NULL OR j.remote = :remote)
              AND (:employmentType IS NULL OR j.employmentType = :employmentType)
              AND (:keyword IS NULL OR :keyword = '' OR
                   LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(j.company) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(j.requiredSkills) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(j.location) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY j.createdAt DESC
            """)
    List<JobPosting> searchJobs(@Param("status") JobStatus status,
                                @Param("remote") Boolean remote,
                                @Param("employmentType") EmploymentType employmentType,
                                @Param("keyword") String keyword);
}
