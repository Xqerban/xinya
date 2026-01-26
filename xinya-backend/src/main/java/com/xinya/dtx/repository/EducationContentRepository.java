package com.xinya.dtx.repository;

import com.xinya.dtx.entity.EducationContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EducationContentRepository extends JpaRepository<EducationContent, String> {
    
    List<EducationContent> findByIsActiveTrueOrderBySortOrderAsc();
    
    Page<EducationContent> findByCategoryAndIsActiveTrue(String category, Pageable pageable);
    
    Page<EducationContent> findByIsActiveTrue(Pageable pageable);
}
