package com.xinya.ops.education.mapper;

import com.xinya.ops.education.entity.OpsEducationContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OpsEducationContentMapper extends JpaRepository<OpsEducationContent, String> {

    @Query("SELECT e FROM OpsEducationContent e WHERE " +
           "(:stage IS NULL OR e.stage = :stage OR e.stage IS NULL) AND " +
           "(:category IS NULL OR e.category = :category) AND " +
           "(:contentType IS NULL OR e.contentType = :contentType) AND " +
           "(:keyword IS NULL OR e.title LIKE CONCAT('%',:keyword,'%')) " +
           "ORDER BY e.sortOrder ASC, e.createdAt DESC")
    Page<OpsEducationContent> findByFilters(
            @Param("stage") String stage,
            @Param("category") String category,
            @Param("contentType") String contentType,
            @Param("keyword") String keyword,
            Pageable pageable);
}
