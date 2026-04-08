package com.xinya.ops.education.service.impl;

import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.response.ApiResponse;
import com.xinya.ops.common.response.PageResult;
import com.xinya.ops.education.dto.CreateEducationContentRequest;
import com.xinya.ops.education.dto.EducationContentDto;
import com.xinya.ops.education.dto.UpdateEducationContentRequest;
import com.xinya.ops.education.entity.OpsEducationContent;
import com.xinya.ops.education.mapper.OpsEducationContentMapper;
import com.xinya.ops.education.service.EducationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        int p = (page == null || page < 1) ? 0 : page - 1;
        int size = (pageSize == null || pageSize <= 0) ? 20 : pageSize;

        Page<OpsEducationContent> result = contentMapper.findByFilters(
                StringUtils.hasText(stage) ? stage : null,
                StringUtils.hasText(category) ? category : null,
                StringUtils.hasText(contentType) ? contentType : null,
                StringUtils.hasText(keyword) ? keyword : null,
                PageRequest.of(p, size));

        return PageResult.<EducationContentDto>builder()
                .list(result.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .total(result.getTotalElements())
                .page(p + 1)
                .pageSize(size)
                .build();
    }

    @Override
    public EducationContentDto getContent(String id) {
        return toDto(contentMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("内容不存在")));
    }

    @Override
    @Transactional
    public EducationContentDto createContent(CreateEducationContentRequest request, String operatorId) {
        String id = "ec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String tagsStr = request.getTags() != null ? String.join(",", request.getTags()) : null;

        OpsEducationContent content = OpsEducationContent.builder()
                .id(id)
                .title(request.getTitle())
                .stage(request.getStage())
                .category(request.getCategory())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .durationSeconds(request.getDurationSeconds())
                .thumbnailUrl(request.getThumbnailUrl())
                .mediaUrl(request.getMediaUrl())
                .tags(tagsStr)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true)
                .syncedToClinical(false)
                .createdBy(operatorId)
                .build();
        contentMapper.save(content);

        // 发布到 xinya-backend
        syncCreateToClinical(content);

        return toDto(content);
    }

    @Override
    @Transactional
    public EducationContentDto updateContent(String id, UpdateEducationContentRequest request) {
        OpsEducationContent content = contentMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("内容不存在"));

        if (StringUtils.hasText(request.getTitle())) content.setTitle(request.getTitle());
        if (request.getStage() != null) content.setStage(request.getStage());
        if (StringUtils.hasText(request.getCategory())) content.setCategory(request.getCategory());
        if (request.getDescription() != null) content.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getContentType())) content.setContentType(request.getContentType());
        if (request.getDurationSeconds() != null) content.setDurationSeconds(request.getDurationSeconds());
        if (request.getThumbnailUrl() != null) content.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getMediaUrl() != null) content.setMediaUrl(request.getMediaUrl());
        if (request.getTags() != null) content.setTags(String.join(",", request.getTags()));
        if (request.getSortOrder() != null) content.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) content.setIsActive(request.getIsActive());

        contentMapper.save(content);

        // 同步更新到 xinya-backend
        syncUpdateToClinical(content);

        return toDto(content);
    }

    @Override
    @Transactional
    public void deactivateContent(String id) {
        OpsEducationContent content = contentMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("内容不存在"));
        content.setIsActive(false);
        contentMapper.save(content);

        // 同步下架到 xinya-backend
        try {
            clinicalApiClient.delete(
                    "/internal/education/sync/" + id,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {});
        } catch (Exception e) {
            log.warn("同步下架宣教内容到 clinical 失败，id={}，错误：{}", id, e.getMessage());
        }
    }

    private void syncCreateToClinical(OpsEducationContent content) {
        try {
            // 构造与 xinya-backend CreateEducationContentRequest 兼容的 payload
            var payload = buildClinicalCreatePayload(content);
            clinicalApiClient.post(
                    "/internal/education/sync",
                    payload,
                    new ParameterizedTypeReference<ApiResponse<EducationContentDto>>() {});
            content.setSyncedToClinical(true);
            contentMapper.save(content);
        } catch (Exception e) {
            log.warn("同步创建宣教内容到 clinical 失败，id={}，错误：{}", content.getId(), e.getMessage());
        }
    }

    private void syncUpdateToClinical(OpsEducationContent content) {
        try {
            var payload = buildClinicalUpdatePayload(content);
            clinicalApiClient.put(
                    "/internal/education/sync/" + content.getId(),
                    payload,
                    new ParameterizedTypeReference<ApiResponse<EducationContentDto>>() {});
        } catch (Exception e) {
            log.warn("同步更新宣教内容到 clinical 失败，id={}，错误：{}", content.getId(), e.getMessage());
        }
    }

    private ClinicalCreatePayload buildClinicalCreatePayload(OpsEducationContent c) {
        return new ClinicalCreatePayload(
                c.getTitle(), c.getStage(), c.getCategory(), c.getDescription(),
                c.getContentType(), c.getDurationSeconds(),
                c.getThumbnailUrl(), c.getMediaUrl(),
                parseTags(c.getTags()), c.getSortOrder());
    }

    private ClinicalUpdatePayload buildClinicalUpdatePayload(OpsEducationContent c) {
        return new ClinicalUpdatePayload(
                c.getTitle(), c.getStage(), c.getCategory(), c.getDescription(),
                c.getContentType(), c.getDurationSeconds(),
                c.getThumbnailUrl(), c.getMediaUrl(),
                parseTags(c.getTags()), c.getSortOrder(), c.getIsActive());
    }

    private List<String> parseTags(String tagsStr) {
        if (!StringUtils.hasText(tagsStr)) return List.of();
        return Arrays.stream(tagsStr.split(",")).map(String::trim).collect(Collectors.toList());
    }

    private EducationContentDto toDto(OpsEducationContent c) {
        return EducationContentDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .stage(c.getStage())
                .category(c.getCategory())
                .description(c.getDescription())
                .contentType(c.getContentType())
                .durationSeconds(c.getDurationSeconds())
                .thumbnailUrl(c.getThumbnailUrl())
                .mediaUrl(c.getMediaUrl())
                .tags(parseTags(c.getTags()))
                .sortOrder(c.getSortOrder())
                .isActive(c.getIsActive())
                .syncedToClinical(c.getSyncedToClinical())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                .updatedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null)
                .build();
    }

    // 与 xinya-backend 接口对齐的内部 Payload 类
    record ClinicalCreatePayload(String title, String stage, String category, String description,
                                  String contentType, Integer durationSeconds,
                                  String thumbnailUrl, String mediaUrl,
                                  List<String> tags, Integer sortOrder) {}

    record ClinicalUpdatePayload(String title, String stage, String category, String description,
                                  String contentType, Integer durationSeconds,
                                  String thumbnailUrl, String mediaUrl,
                                  List<String> tags, Integer sortOrder, Boolean isActive) {}
}
