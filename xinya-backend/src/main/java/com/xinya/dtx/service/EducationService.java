package com.xinya.dtx.service;

import com.xinya.dtx.dto.EducationDto;
import com.xinya.dtx.entity.EducationContent;
import com.xinya.dtx.repository.EducationContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EducationService {
    
    private final EducationContentRepository educationContentRepository;
    
    public List<EducationDto> getContents(String category, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize);
        
        Page<EducationContent> contents;
        if (category != null && !category.isEmpty()) {
            contents = educationContentRepository.findByCategoryAndIsActiveTrue(category, pageRequest);
        } else {
            contents = educationContentRepository.findByIsActiveTrue(pageRequest);
        }
        
        // 如果数据库为空，返回Mock数据
        if (contents.isEmpty()) {
            return getMockContents();
        }
        
        return contents.getContent().stream()
            .map(EducationDto::fromEntity)
            .toList();
    }
    
    public int getTotalCount(String category) {
        if (category != null && !category.isEmpty()) {
            return (int) educationContentRepository.findByCategoryAndIsActiveTrue(
                category, PageRequest.of(0, 1)).getTotalElements();
        }
        return (int) educationContentRepository.findByIsActiveTrue(PageRequest.of(0, 1)).getTotalElements();
    }
    
    /**
     * Mock数据（开发阶段使用）
     */
    private List<EducationDto> getMockContents() {
        List<EducationDto> mockList = new ArrayList<>();
        
        mockList.add(EducationDto.builder()
            .id("1")
            .title("认识骨髓移植")
            .category("入仓准备")
            .description("了解骨髓移植的基本流程")
            .contentType("video")
            .durationSeconds(320)
            .tags(List.of("基础知识", "入门"))
            .build());
        
        mockList.add(EducationDto.builder()
            .id("2")
            .title("预处理期护理要点")
            .category("预处理")
            .description("预处理期间的注意事项")
            .contentType("video")
            .durationSeconds(450)
            .tags(List.of("预处理", "护理"))
            .build());
        
        mockList.add(EducationDto.builder()
            .id("3")
            .title("感染预防指南")
            .category("康复护理")
            .description("如何有效预防感染")
            .contentType("video")
            .durationSeconds(280)
            .tags(List.of("感染预防", "护理"))
            .build());
        
        mockList.add(EducationDto.builder()
            .id("4")
            .title("口腔护理技巧")
            .category("康复护理")
            .description("保持口腔健康的方法")
            .contentType("video")
            .durationSeconds(200)
            .tags(List.of("口腔护理"))
            .build());
        
        mockList.add(EducationDto.builder()
            .id("5")
            .title("饮食禁忌须知")
            .category("饮食指导")
            .description("移植期间的饮食注意事项")
            .contentType("video")
            .durationSeconds(360)
            .tags(List.of("饮食", "禁忌"))
            .build());
        
        mockList.add(EducationDto.builder()
            .id("6")
            .title("情绪调节方法")
            .category("心理支持")
            .description("保持积极心态的技巧")
            .contentType("video")
            .durationSeconds(400)
            .tags(List.of("心理", "情绪"))
            .build());
        
        return mockList;
    }
}
