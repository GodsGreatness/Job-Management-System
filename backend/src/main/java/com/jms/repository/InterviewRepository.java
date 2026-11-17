package com.jms.repository;

import com.jms.domain.enums.InterviewStatus;
import com.jms.domain.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByApplicationIdOrderByScheduledAtAsc(Long applicationId);

    List<Interview> findByApplicationCandidateIdOrderByScheduledAtAsc(Long candidateId);

    List<Interview> findByInterviewerIdOrderByScheduledAtAsc(Long interviewerId);

    List<Interview> findAllByOrderByScheduledAtDesc();

    long countByStatus(InterviewStatus status);
}
