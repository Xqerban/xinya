package com.xinya.ops.config.mapper;

import com.xinya.ops.config.entity.OpsProQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpsProQuestionMapper extends JpaRepository<OpsProQuestion, String> {

    @Query("SELECT q FROM OpsProQuestion q WHERE (:stage IS NULL OR q.stage = :stage) ORDER BY q.sortOrder ASC")
    List<OpsProQuestion> findByStage(@Param("stage") String stage);

    List<OpsProQuestion> findAllByOrderBySortOrderAsc();
}
