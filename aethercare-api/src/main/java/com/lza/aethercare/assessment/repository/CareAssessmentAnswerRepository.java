package com.lza.aethercare.assessment.repository;

import com.lza.aethercare.assessment.entity.CareAssessmentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Assessment answer repository。 */
@Repository
public interface CareAssessmentAnswerRepository extends JpaRepository<CareAssessmentAnswer, Long> {

    List<CareAssessmentAnswer> findByWorkflowIdOrderByCreatedAtAsc(Long workflowId);
}
