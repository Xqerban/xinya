package com.xinya.dtx.education.service.impl;

import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.education.dto.*;
import com.xinya.dtx.education.service.EducationService;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowRequest;
import com.xinya.dtx.hopetree.service.HopeTreeService;
import com.xinya.dtx.patient.mapper.PatientMapper;
import com.xinya.dtx.system.entity.EducationContent;
import com.xinya.dtx.system.entity.EducationProgress;
import com.xinya.dtx.system.mapper.EducationContentMapper;
import com.xinya.dtx.system.mapper.EducationProgressMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EducationServiceImpl implements EducationService {

    private static final int EDUCATION_COMPLETE_EXP = 15;

    private final EducationContentMapper contentMapper;
    private final EducationProgressMapper progressMapper;
    private final PatientMapper patientMapper;
    private final HopeTreeService hopeTreeService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<EducationContentDto> getContents(String stage, String category, String contentType,
                                                       String keyword, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        var pageable = org.springframework.data.domain.PageRequest.of(p, size);
        var contentPage = contentMapper.findByFilters(
                StringUtils.hasText(stage) ? stage : null,
                StringUtils.hasText(category) ? category : null,
                StringUtils.hasText(contentType) ? contentType : null,
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                pageable);
        List<EducationContentDto> list = contentPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageResult.<EducationContentDto>builder()
                .list(list)
                .total(contentPage.getTotalElements())
                .page(p + 1)
                .pageSize(size)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EducationContentDto getContentDetail(String id) {
        EducationContent c = contentMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("宣教内容不存在"));
        if (!Boolean.TRUE.equals(c.getIsActive())) {
            throw new EntityNotFoundException("宣教内容已下架");
        }
        return toDto(c);
    }

    @Override
    @Transactional
    public EducationProgressResponse recordProgress(EducationProgressRequest request) {
        if (!patientMapper.existsById(request.getPatientId())) {
            throw new EntityNotFoundException("患者不存在");
        }
        EducationContent content = contentMapper.findById(request.getContentId())
                .orElseThrow(() -> new EntityNotFoundException("宣教内容不存在"));

        LocalDateTime now = LocalDateTime.now();
        int seconds = request.getWatchedSeconds() != null ? request.getWatchedSeconds() : 0;
        boolean completed = Boolean.TRUE.equals(request.getCompleted());

        EducationProgress progress = progressMapper.findByPatientIdAndContentId(request.getPatientId(), request.getContentId())
                .orElse(null);

        if (progress == null) {
            progress = EducationProgress.builder()
                    .patientId(request.getPatientId())
                    .contentId(request.getContentId())
                    .watchedSeconds(seconds)
                    .completed(completed)
                    .rewardGiven(false)
                    .lastWatchedAt(now)
                    .build();
            progressMapper.save(progress);
        } else {
            progress.setWatchedSeconds(seconds);
            progress.setLastWatchedAt(now);
            if (completed) {
                progress.setCompleted(true);
            }
            progressMapper.save(progress);
        }

        int hopeTreeExpDelta = 0;
        if (completed) {
            int updated = progressMapper.markCompletedAndReward(request.getPatientId(), request.getContentId());
            if (updated == 1) {
                hopeTreeService.grow(HopeTreeGrowRequest.builder()
                        .patientId(request.getPatientId())
                        .growthSource("education")
                        .expAmount(EDUCATION_COMPLETE_EXP)
                        .build());
                hopeTreeExpDelta = EDUCATION_COMPLETE_EXP;
            }
        }

        double completionRate = content.getDurationSeconds() != null && content.getDurationSeconds() > 0
                ? Math.min(1.0, (double) seconds / content.getDurationSeconds())
                : 0.0;

        return EducationProgressResponse.builder()
                .hopeTreeExpDelta(hopeTreeExpDelta)
                .completionRate(completionRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientProgressSummaryDto getPatientProgress(String patientId) {
        if (!patientMapper.existsById(patientId)) {
            throw new EntityNotFoundException("患者不存在");
        }
        int totalContents = (int) contentMapper.countByIsActiveTrue();
        int completedContents = (int) progressMapper.countByPatientIdAndCompletedTrue(patientId);
        int totalWatchedSeconds = progressMapper.sumWatchedSecondsByPatientId(patientId);
        double completionRate = totalContents > 0 ? (double) completedContents / totalContents : 0.0;

        List<EducationProgress> progressList = progressMapper.findByPatientIdOrderByLastWatchedAtDesc(patientId);
        List<String> contentIds = progressList.stream().map(EducationProgress::getContentId).distinct().collect(Collectors.toList());
        Map<String, EducationContent> contentMap = contentIds.isEmpty() ? Map.of()
                : contentMapper.findAllById(contentIds).stream().collect(Collectors.toMap(EducationContent::getId, x -> x));

        List<PatientProgressItemDto> items = progressList.stream()
                .map(p -> {
                    EducationContent c = contentMap.get(p.getContentId());
                    return PatientProgressItemDto.builder()
                            .contentId(p.getContentId())
                            .contentTitle(c != null ? c.getTitle() : null)
                            .watchedSeconds(p.getWatchedSeconds())
                            .completed(Boolean.TRUE.equals(p.getCompleted()))
                            .lastWatchedAt(p.getLastWatchedAt() != null ? p.getLastWatchedAt().toString() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return PatientProgressSummaryDto.builder()
                .totalContents(totalContents)
                .completedContents(completedContents)
                .completionRate(completionRate)
                .totalWatchedSeconds(totalWatchedSeconds)
                .progressList(items)
                .build();
    }

    @Override
    @Transactional
    public EducationContentDto createContent(CreateEducationContentRequest request) {
        String id = "ec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        EducationContent c = EducationContent.builder()
                .id(id)
                .title(request.getTitle())
                .stage(request.getStage())
                .category(request.getCategory())
                .description(request.getDescription())
                .contentType(request.getContentType())
                .durationSeconds(request.getDurationSeconds())
                .thumbnailUrl(request.getThumbnailUrl())
                .mediaUrl(request.getMediaUrl())
                .tags(tagsToString(request.getTags()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(true)
                .build();
        contentMapper.save(c);
        return toDto(c);
    }

    @Override
    @Transactional
    public EducationContentDto updateContent(String id, UpdateEducationContentRequest request) {
        EducationContent c = contentMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("宣教内容不存在"));
        if (request.getTitle() != null) c.setTitle(request.getTitle());
        if (request.getStage() != null) c.setStage(request.getStage());
        if (request.getCategory() != null) c.setCategory(request.getCategory());
        if (request.getDescription() != null) c.setDescription(request.getDescription());
        if (request.getContentType() != null) c.setContentType(request.getContentType());
        if (request.getDurationSeconds() != null) c.setDurationSeconds(request.getDurationSeconds());
        if (request.getThumbnailUrl() != null) c.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getMediaUrl() != null) c.setMediaUrl(request.getMediaUrl());
        if (request.getTags() != null) c.setTags(tagsToString(request.getTags()));
        if (request.getSortOrder() != null) c.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) c.setIsActive(request.getIsActive());
        contentMapper.save(c);
        return toDto(c);
    }

    @Override
    @Transactional
    public void deactivateContent(String id) {
        if (!contentMapper.existsById(id)) {
            throw new EntityNotFoundException("宣教内容不存在");
        }
        contentMapper.deactivate(id);
    }

    private EducationContentDto toDto(EducationContent c) {
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
                .tags(tagsToList(c.getTags()))
                .sortOrder(c.getSortOrder())
                .isActive(c.getIsActive())
                .build();
    }

    private static List<String> tagsToList(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private static String tagsToString(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return String.join(",", tags);
    }
}
