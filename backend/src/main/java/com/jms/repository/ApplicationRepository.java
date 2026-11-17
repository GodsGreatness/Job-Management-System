package com.jms.repository;

import com.jms.domain.enums.ApplicationStatus;
import com.jms.domain.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    Optional<Application> findByJobIdAndCandidateId(Long jobId, Long candidateId);

    List<Application> findByCandidateIdOrderByAppliedAtDesc(Long candidateId);

    List<Application> findByJobIdOrderByAtsMatchScoreDescAppliedAtDesc(Long jobId);

    List<Application> findByJobEmployerIdOrderByAppliedAtDesc(Long employerId);

    List<Application> findAllByOrderByAppliedAtDesc();

    long countByStatus(ApplicationStatus status);

    long countByJobId(Long jobId);

    @Query("SELECT AVG(a.atsMatchScore) FROM Application a WHERE a.atsMatchScore IS NOT NULL")
    Double findAverageAtsScore();

    @Query("SELECT a FROM Application a JOIN FETCH a.job JOIN FETCH a.candidate WHERE a.id = :id")
    Optional<Application> findDetailedById(@Param("id") Long id);
}
