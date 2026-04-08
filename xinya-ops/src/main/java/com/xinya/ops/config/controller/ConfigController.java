package com.xinya.ops.config.controller;

import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.response.ApiResponse;
import com.xinya.ops.config.dto.CreateCrisisKeywordRequest;
import com.xinya.ops.config.dto.CrisisKeywordDto;
import com.xinya.ops.config.entity.OpsCrisisKeyword;
import com.xinya.ops.config.entity.OpsProQuestion;
import com.xinya.ops.config.mapper.OpsCrisisKeywordMapper;
import com.xinya.ops.config.mapper.OpsProQuestionMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ConfigController {

    private final OpsCrisisKeywordMapper crisisKeywordMapper;
    private final OpsProQuestionMapper proQuestionMapper;
    private final ClinicalApiClient clinicalApiClient;

    // ========== 危机关键词 ==========

    @GetMapping("/crisis-keywords")
    public ApiResponse<List<CrisisKeywordDto>> listCrisisKeywords() {
        List<CrisisKeywordDto> list = crisisKeywordMapper.findByIsActiveTrueOrderByCrisisLevelDescKeywordAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @PostMapping("/crisis-keywords")
    public ApiResponse<CrisisKeywordDto> createCrisisKeyword(
            @Valid @RequestBody CreateCrisisKeywordRequest request,
            HttpServletRequest httpRequest) {
        if (crisisKeywordMapper.existsByKeyword(request.getKeyword())) {
            return ApiResponse.error(400, "关键词已存在");
        }
        String operatorId = (String) httpRequest.getAttribute("userId");
        OpsCrisisKeyword kw = OpsCrisisKeyword.builder()
                .keyword(request.getKeyword())
                .crisisLevel(request.getCrisisLevel())
                .isActive(true)
                .createdBy(operatorId)
                .build();
        crisisKeywordMapper.save(kw);

        syncAllCrisisKeywordsToClinical();

        return ApiResponse.success(toDto(kw));
    }

    @DeleteMapping("/crisis-keywords/{id}")
    public ApiResponse<Void> deleteCrisisKeyword(@PathVariable Long id) {
        if (!crisisKeywordMapper.existsById(id)) {
            return ApiResponse.error(404, "关键词不存在");
        }
        crisisKeywordMapper.deactivate(id);
        syncAllCrisisKeywordsToClinical();
        return ApiResponse.success(null);
    }

    // ========== PRO 题目配置 ==========

    @GetMapping("/pro-questions")
    public ApiResponse<List<OpsProQuestion>> listProQuestions(
            @RequestParam(required = false) String stage) {
        List<OpsProQuestion> list = StringUtils.hasText(stage)
                ? proQuestionMapper.findByStage(stage)
                : proQuestionMapper.findAllByOrderBySortOrderAsc();
        return ApiResponse.success(list);
    }

    @PutMapping("/pro-questions/{id}")
    public ApiResponse<OpsProQuestion> updateProQuestion(
            @PathVariable String id,
            @RequestBody OpsProQuestion request) {
        OpsProQuestion q = proQuestionMapper.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("题目不存在"));
        if (StringUtils.hasText(request.getTitle())) q.setTitle(request.getTitle());
        if (request.getSortOrder() != null) q.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) q.setIsActive(request.getIsActive());
        proQuestionMapper.save(q);

        syncProQuestionToClinical(q);

        return ApiResponse.success(q);
    }

    // ========== 同步到 clinical ==========

    private void syncAllCrisisKeywordsToClinical() {
        try {
            List<Map<String, Object>> payload = crisisKeywordMapper
                    .findByIsActiveTrueOrderByCrisisLevelDescKeywordAsc()
                    .stream()
                    .map(k -> Map.of(
                            "keyword", (Object) k.getKeyword(),
                            "crisisLevel", k.getCrisisLevel(),
                            "isActive", k.getIsActive()))
                    .collect(Collectors.toList());
            clinicalApiClient.post(
                    "/internal/config/crisis-keywords",
                    payload,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {});
        } catch (Exception e) {
            log.warn("同步危机关键词到 clinical 失败：{}", e.getMessage());
        }
    }

    private void syncProQuestionToClinical(OpsProQuestion q) {
        try {
            clinicalApiClient.post(
                    "/internal/config/pro-questions",
                    q,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {});
        } catch (Exception e) {
            log.warn("同步PRO题目到 clinical 失败，id={}，错误：{}", q.getId(), e.getMessage());
        }
    }

    private CrisisKeywordDto toDto(OpsCrisisKeyword k) {
        return CrisisKeywordDto.builder()
                .id(k.getId())
                .keyword(k.getKeyword())
                .crisisLevel(k.getCrisisLevel())
                .isActive(k.getIsActive())
                .createdBy(k.getCreatedBy())
                .createdAt(k.getCreatedAt() != null ? k.getCreatedAt().toString() : null)
                .build();
    }
}
