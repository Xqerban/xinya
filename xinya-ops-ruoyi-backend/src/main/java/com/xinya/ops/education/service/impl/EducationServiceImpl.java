package com.xinya.ops.education.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.domain.PageResult;
import com.xinya.ops.common.domain.R;
import com.xinya.ops.education.dto.CreateEducationContentRequest;
import com.xinya.ops.education.dto.EducationContentDto;
import com.xinya.ops.education.dto.UpdateEducationContentRequest;
import com.xinya.ops.education.entity.OpsEducationContent;
import com.xinya.ops.education.mapper.OpsEducationContentMapper;
import com.xinya.ops.education.service.EducationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EducationServiceImpl implements EducationService {

    private final OpsEducationContentMapper contentMapper;
    private final ClinicalApiClient clinicalApiClient;

    @Override
    public PageResult<EducationContentDto> listContents(String stage, String category,
            String contentType, String keyword, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize <= 0) ? 20 : pageSize;

        var result = contentMapper.findByFilters(
                new Page<>(p, size),
                StringUtils.hasText(stage) ? stage : null,
                StringUtils.hasText(category) ? category : null,
                StringUtils.hasText(contentType) ? contentType : null,
                StringUtils.hasText(keyword) ? keyword : null);

        return PageResult.<EducationContentDto>builder()
                .list(result.getRecords().stream().map(this::toDto).collect(Collectors.toList()))
                .total(result.getTotal()).page(p).pageSize(size).build();
    }

    @Override
    public EducationContentDto getContent(String id) {
        OpsEducationContent c = contentMapper.selectById(id);
        if (c == null) throw new IllegalArgumentException("内容不存在");
        return toDto(c);
    }

    @Override
    @Transactional
    public EducationContentDto createContent(CreateEducationContentRequest request, String operatorId) {
        String id = "ec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tagsStr = request.getTags() != null ? String.join(",", request.getTags()) : null;

        OpsEducationContent content = OpsEducationContent.builder()
                .id(id).title(request.getTitle()).stage(request.getStage())
                .category(request.getCategory()).description(request.getDescription())
                .contentType(request.getContentType()).durationSeconds(request.getDurationSeconds())
                .thumbnailUrl(request.getThumbnailUrl()).mediaUrl(request.getMediaUrl())
                .tags(tagsStr).sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true).syncedToClinical(false).createdBy(operatorId).build();
        contentMapper.insert(content);
        syncCreateToClinical(content);
        return toDto(content);
    }

    @Override
    @Transactional
    public EducationContentDto updateContent(String id, UpdateEducationContentRequest request) {
        OpsEducationContent c = contentMapper.selectById(id);
        if (c == null) throw new IllegalArgumentException("内容不存在");

        if (StringUtils.hasText(request.getTitle())) c.setTitle(request.getTitle());
        if (request.getStage() != null) c.setStage(request.getStage());
        if (StringUtils.hasText(request.getCategory())) c.setCategory(request.getCategory());
        if (request.getDescription() != null) c.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getContentType())) c.setContentType(request.getContentType());
        if (request.getDurationSeconds() != null) c.setDurationSeconds(request.getDurationSeconds());
        if (request.getThumbnailUrl() != null) c.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getMediaUrl() != null) c.setMediaUrl(request.getMediaUrl());
        if (request.getTags() != null) c.setTags(String.join(",", request.getTags()));
        if (request.getSortOrder() != null) c.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) c.setIsActive(request.getIsActive());
        contentMapper.updateById(c);
        syncUpdateToClinical(c);
        return toDto(c);
    }

    @Override
    @Transactional
    public void deactivateContent(String id) {
        OpsEducationContent c = contentMapper.selectById(id);
        if (c == null) throw new IllegalArgumentException("内容不存在");
        c.setIsActive(false);
        contentMapper.updateById(c);
        try {
            clinicalApiClient.delete("/internal/education/sync/" + id,
                    new ParameterizedTypeReference<R<Void>>() {});
        } catch (Exception e) {
            log.warn("同步下架宣教内容到 clinical 失败，id={}，错误：{}", id, e.getMessage());
        }
    }

    private void syncCreateToClinical(OpsEducationContent c) {
        try {
            clinicalApiClient.post("/internal/education/sync",
                    buildClinicalCreatePayload(c),
                    new ParameterizedTypeReference<R<EducationContentDto>>() {});
            c.setSyncedToClinical(true);
            contentMapper.updateById(c);
        } catch (Exception e) {
            log.warn("同步创建宣教内容到 clinical 失败，id={}：{}", c.getId(), e.getMessage());
        }
    }

    private void syncUpdateToClinical(OpsEducationContent c) {
        try {
            clinicalApiClient.put("/internal/education/sync/" + c.getId(),
                    buildClinicalUpdatePayload(c),
                    new ParameterizedTypeReference<R<EducationContentDto>>() {});
        } catch (Exception e) {
            log.warn("同步更新宣教内容到 clinical 失败，id={}：{}", c.getId(), e.getMessage());
        }
    }

    private List<String> parseTags(String tagsStr) {
        if (!StringUtils.hasText(tagsStr)) return List.of();
        return Arrays.stream(tagsStr.split(",")).map(String::trim).collect(Collectors.toList());
    }

    private EducationContentDto toDto(OpsEducationContent c) {
        return EducationContentDto.builder()
                .id(c.getId()).title(c.getTitle()).stage(c.getStage())
                .category(c.getCategory()).description(c.getDescription())
                .contentType(c.getContentType()).durationSeconds(c.getDurationSeconds())
                .thumbnailUrl(c.getThumbnailUrl()).mediaUrl(c.getMediaUrl())
                .tags(parseTags(c.getTags())).sortOrder(c.getSortOrder())
                .isActive(c.getIsActive()).syncedToClinical(c.getSyncedToClinical())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null)
                .build();
    }

    private record ClinicalCreatePayload(String title, String stage, String category,
            String description, String contentType, Integer durationSeconds,
            String thumbnailUrl, String mediaUrl, List<String> tags, Integer sortOrder) {}

    private record ClinicalUpdatePayload(String title, String stage, String category,
            String description, String contentType, Integer durationSeconds,
            String thumbnailUrl, String mediaUrl, List<String> tags, Integer sortOrder, Boolean isActive) {}

    private ClinicalCreatePayload buildClinicalCreatePayload(OpsEducationContent c) {
        return new ClinicalCreatePayload(c.getTitle(), c.getStage(), c.getCategory(),
                c.getDescription(), c.getContentType(), c.getDurationSeconds(),
                c.getThumbnailUrl(), c.getMediaUrl(), parseTags(c.getTags()), c.getSortOrder());
    }

    private ClinicalUpdatePayload buildClinicalUpdatePayload(OpsEducationContent c) {
        return new ClinicalUpdatePayload(c.getTitle(), c.getStage(), c.getCategory(),
                c.getDescription(), c.getContentType(), c.getDurationSeconds(),
                c.getThumbnailUrl(), c.getMediaUrl(), parseTags(c.getTags()), c.getSortOrder(), c.getIsActive());
    }
}
