package com.xinya.business.education.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.education.dto.EducationContentDto;
import com.xinya.business.education.dto.EducationProgressDto;
import com.xinya.business.education.entity.EducationContent;
import com.xinya.business.education.entity.EducationProgress;
import com.xinya.business.education.mapper.EducationContentMapper;
import com.xinya.business.education.mapper.EducationProgressMapper;
import com.xinya.business.education.service.EducationService;
import com.xinya.business.hopetree.service.HopeTreeService;
import com.xinya.common.core.domain.PageResult;
import com.xinya.common.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EducationServiceImpl implements EducationService {

    private final EducationContentMapper contentMapper;
    private final EducationProgressMapper progressMapper;
    private final HopeTreeService hopeTreeService;

    @Override
    public PageResult<EducationContentDto> listContents(Integer page, Integer pageSize, String category, String stage) {
        int pageIndex = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Page<EducationContent> p = new Page<>(pageIndex, size);
        contentMapper.findByFilters(p,
                (stage != null && !stage.isBlank()) ? stage : null,
                (category != null && !category.isBlank()) ? category : null,
                null, null);
        return PageResult.<EducationContentDto>builder()
                .list(p.getRecords().stream().map(this::toContentDto).collect(Collectors.toList()))
                .total(p.getTotal())
                .page(pageIndex)
                .pageSize(size)
                .build();
    }

    @Override
    public EducationContentDto getContentById(String id) {
        EducationContent content = contentMapper.selectById(id);
        if (content == null) throw new ResourceNotFoundException("内容不存在");
        return toContentDto(content);
    }

    @Override
    @Transactional
    public EducationProgressDto markProgress(String patientId, String contentId) {
        if (contentMapper.selectById(contentId) == null) throw new ResourceNotFoundException("内容不存在");

        EducationProgress progress = progressMapper.findByPatientIdAndContentId(patientId, contentId);
        if (progress == null) {
            progress = EducationProgress.builder()
                    .patientId(patientId)
                    .contentId(contentId)
                    .completed(true)
                    .build();
            progressMapper.insert(progress);
            try {
                hopeTreeService.addGrowthExp(patientId, 5, "EDUCATION", "完成教育内容: " + contentId);
            } catch (Exception ignored) {}
        } else if (Boolean.FALSE.equals(progress.getCompleted())) {
            progress.setCompleted(true);
            progressMapper.updateById(progress);
            try {
                hopeTreeService.addGrowthExp(patientId, 5, "EDUCATION", "完成教育内容: " + contentId);
            } catch (Exception ignored) {}
        }
        return toProgressDto(progress);
    }

    @Override
    public List<EducationProgressDto> getPatientProgress(String patientId) {
        return progressMapper.findByPatientIdOrderByLastWatchedAtDesc(patientId).stream()
                .map(this::toProgressDto)
                .collect(Collectors.toList());
    }

    private EducationContentDto toContentDto(EducationContent c) {
        return EducationContentDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .category(c.getCategory())
                .stage(c.getStage())
                .contentType(c.getContentType())
                .contentUrl(c.getMediaUrl())
                .thumbnailUrl(c.getThumbnailUrl())
                .description(c.getDescription())
                .durationMinutes(c.getDurationSeconds() != null ? c.getDurationSeconds() / 60 : null)
                .sortOrder(c.getSortOrder())
                .build();
    }

    private EducationProgressDto toProgressDto(EducationProgress p) {
        return EducationProgressDto.builder()
                .id(p.getId() != null ? p.getId().toString() : null)
                .patientId(p.getPatientId())
                .contentId(p.getContentId())
                .completed(p.getCompleted())
                .completedAt(p.getLastWatchedAt() != null ? p.getLastWatchedAt().toString() : null)
                .build();
    }
}
