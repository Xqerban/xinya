package com.xinya.dtx.system.mapper;

import com.xinya.dtx.system.entity.EducationContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EducationContentMapper extends JpaRepository<EducationContent, String> {

    /** 多条件筛选（stage/category/contentType 均可为 null 时不过滤） */
    @Query("SELECT e FROM EducationContent e WHERE e.isActive = true " +
           "AND (:stage IS NULL OR e.stage = :stage OR e.stage IS NULL) " +
           "AND (:category IS NULL OR e.category = :category) " +
           "AND (:contentType IS NULL OR e.contentType = :contentType) " +
           "AND (:keyword IS NULL OR e.title LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY e.sortOrder ASC, e.createdAt DESC")
    Page<EducationContent> findByFilters(@Param("stage") String stage,
                                         @Param("category") String category,
                                         @Param("contentType") String contentType,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    /** 按 ID 列表批量查询（AI 推荐内容详情补全） */
    List<EducationContent> findByIdInAndIsActiveTrueOrderBySortOrderAsc(List<String> ids);

    /** 查询指定阶段的全部上架内容（不分页，用于数量统计） */
    @Query("SELECT e FROM EducationContent e WHERE e.isActive = true " +
           "AND (e.stage = :stage OR e.stage IS NULL) ORDER BY e.sortOrder ASC")
    List<EducationContent> findActiveByStage(@Param("stage") String stage);

    /** 统计上架内容总数（患者宣教进度用） */
    long countByIsActiveTrue();

    /** 统计某阶段上架内容数量 */
    @Query("SELECT COUNT(e) FROM EducationContent e WHERE e.isActive = true " +
           "AND (e.stage = :stage OR e.stage IS NULL)")
    long countActiveByStage(@Param("stage") String stage);

    /** 逻辑下架（isActive 置 false） */
    @Modifying
    @Query("UPDATE EducationContent e SET e.isActive = false, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.id = :id")
    void deactivate(@Param("id") String id);

    /** 上架 */
    @Modifying
    @Query("UPDATE EducationContent e SET e.isActive = true, e.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE e.id = :id")
    void activate(@Param("id") String id);
}
