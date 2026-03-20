package com.xinya.dtx.internal.controller;

import com.xinya.dtx.agent.entity.CrisisKeyword;
import com.xinya.dtx.agent.mapper.CrisisKeywordMapper;
import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.education.dto.CreateEducationContentRequest;
import com.xinya.dtx.education.dto.EducationContentDto;
import com.xinya.dtx.education.dto.UpdateEducationContentRequest;
import com.xinya.dtx.education.service.EducationService;
import com.xinya.dtx.internal.dto.SyncCrisisKeywordRequest;
import com.xinya.dtx.internal.dto.SyncProQuestionRequest;
import com.xinya.dtx.internal.dto.SyncUserRequest;
import com.xinya.dtx.pro.entity.ProQuestion;
import com.xinya.dtx.pro.mapper.ProQuestionMapper;
import com.xinya.dtx.user.entity.User;
import com.xinya.dtx.user.mapper.UserMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 内部数据同步接口（仅供 xinya-ops 调用，需携带 X-Internal-Key）
 * 接收 ops 端推送的教育内容、危机关键词、PRO 题目、用户账号变更
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalSyncController {

    private final EducationService educationService;
    private final CrisisKeywordMapper crisisKeywordMapper;
    private final ProQuestionMapper proQuestionMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    // ==================== 宣教内容同步 ====================

    /**
     * 新建宣教内容（ops 发布时推送到 clinical）
     */
    @PostMapping("/education/sync")
    public ApiResponse<EducationContentDto> syncCreateContent(
            @Valid @RequestBody CreateEducationContentRequest request) {
        EducationContentDto dto = educationService.createContent(request);
        return ApiResponse.success(dto);
    }

    /**
     * 更新宣教内容
     */
    @PutMapping("/education/sync/{id}")
    public ApiResponse<EducationContentDto> syncUpdateContent(
            @PathVariable String id,
            @RequestBody UpdateEducationContentRequest request) {
        try {
            EducationContentDto dto = educationService.updateContent(id, request);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    /**
     * 下架宣教内容
     */
    @DeleteMapping("/education/sync/{id}")
    public ApiResponse<Void> syncDeactivateContent(@PathVariable String id) {
        try {
            educationService.deactivateContent(id);
            return ApiResponse.success(null);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    // ==================== 危机关键词同步 ====================

    /**
     * 批量覆盖同步危机关键词（ops 变更后全量推送）
     */
    @PostMapping("/config/crisis-keywords")
    public ApiResponse<Void> syncCrisisKeywords(@RequestBody List<SyncCrisisKeywordRequest> keywords) {
        for (SyncCrisisKeywordRequest req : keywords) {
            crisisKeywordMapper.findByKeyword(req.getKeyword()).ifPresentOrElse(
                existing -> {
                    existing.setCrisisLevel(req.getCrisisLevel());
                    existing.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
                    crisisKeywordMapper.save(existing);
                },
                () -> {
                    CrisisKeyword kw = CrisisKeyword.builder()
                            .keyword(req.getKeyword())
                            .crisisLevel(req.getCrisisLevel())
                            .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                            .createdBy("ops-sync")
                            .build();
                    crisisKeywordMapper.save(kw);
                }
            );
        }
        return ApiResponse.success(null);
    }

    // ==================== PRO 题目同步 ====================

    /**
     * 同步单条 PRO 题目（ops 变更后推送）
     */
    @PostMapping("/config/pro-questions")
    public ApiResponse<Void> syncProQuestion(@RequestBody SyncProQuestionRequest req) {
        proQuestionMapper.findById(req.getId()).ifPresentOrElse(
            existing -> {
                existing.setTitle(req.getTitle());
                existing.setStage(req.getStage());
                existing.setType(req.getType());
                existing.setOptions(req.getOptions());
                existing.setScaleMin(req.getScaleMin());
                existing.setScaleMax(req.getScaleMax());
                existing.setMinLabel(req.getMinLabel());
                existing.setMaxLabel(req.getMaxLabel());
                existing.setSymptomKey(req.getSymptomKey());
                existing.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
                existing.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
                proQuestionMapper.save(existing);
            },
            () -> {
                ProQuestion q = ProQuestion.builder()
                        .id(req.getId())
                        .stage(req.getStage())
                        .title(req.getTitle())
                        .type(req.getType())
                        .options(req.getOptions())
                        .scaleMin(req.getScaleMin())
                        .scaleMax(req.getScaleMax())
                        .minLabel(req.getMinLabel())
                        .maxLabel(req.getMaxLabel())
                        .symptomKey(req.getSymptomKey())
                        .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                        .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                        .build();
                proQuestionMapper.save(q);
            }
        );
        return ApiResponse.success(null);
    }

    // ==================== 用户账号同步 ====================

    /**
     * 同步医护用户（ops 创建/更新/停用后推送到 clinical，使 clinical JWT 鉴权可以验证用户）
     */
    @PostMapping("/users/sync")
    public ApiResponse<Void> syncUser(@Valid @RequestBody SyncUserRequest req) {
        userMapper.findByUsername(req.getUsername()).ifPresentOrElse(
            existing -> {
                existing.setDisplayName(req.getDisplayName());
                existing.setRole(req.getRole());
                existing.setPhone(req.getPhone());
                if (req.getEnabled() != null) {
                    existing.setEnabled(req.getEnabled());
                }
                if (req.getPasswordHash() != null) {
                    existing.setPasswordHash(req.getPasswordHash());
                }
                userMapper.save(existing);
            },
            () -> {
                String id = req.getId() != null ? req.getId() : UUID.randomUUID().toString();
                String hash = req.getPasswordHash() != null
                        ? req.getPasswordHash()
                        : passwordEncoder.encode(UUID.randomUUID().toString());
                User user = User.builder()
                        .id(id)
                        .username(req.getUsername())
                        .passwordHash(hash)
                        .displayName(req.getDisplayName())
                        .role(req.getRole())
                        .phone(req.getPhone())
                        .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                        .build();
                userMapper.save(user);
            }
        );
        return ApiResponse.success(null);
    }
}
