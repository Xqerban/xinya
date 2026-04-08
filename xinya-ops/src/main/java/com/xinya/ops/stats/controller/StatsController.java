package com.xinya.ops.stats.controller;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运维端统计数据接口
 * 所有数据均从 xinya-backend /internal/stats/* 获取，不在 ops 端存储临床数据
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ClinicalApiClient clinicalApiClient;

    /**
     * 总览：患者总数、阶段分布、平均心理能量、打卡率、症状趋势、最新预警
     */
    @GetMapping("/overview")
    public ApiResponse<Object> getOverview() {
        ApiResponse<Object> resp = clinicalApiClient.get(
                "/internal/stats/overview",
                new ParameterizedTypeReference<ApiResponse<Object>>() {});
        return resp != null ? resp : ApiResponse.error(502, "获取临床数据失败");
    }

    /**
     * 心理状态分布（健康/轻度/预警三段分布）
     */
    @GetMapping("/psych-distribution")
    public ApiResponse<Object> getPsychDistribution() {
        ApiResponse<Object> resp = clinicalApiClient.get(
                "/internal/stats/psych-distribution",
                new ParameterizedTypeReference<ApiResponse<Object>>() {});
        return resp != null ? resp : ApiResponse.error(502, "获取临床数据失败");
    }

    /**
     * 症状热力图（近 N 天各症状得分矩阵）
     */
    @GetMapping("/symptom-heatmap")
    public ApiResponse<Object> getSymptomHeatmap(
            @RequestParam(required = false) Integer days) {
        String path = "/internal/stats/symptom-heatmap" + (days != null ? "?days=" + days : "");
        ApiResponse<Object> resp = clinicalApiClient.get(
                path,
                new ParameterizedTypeReference<ApiResponse<Object>>() {});
        return resp != null ? resp : ApiResponse.error(502, "获取临床数据失败");
    }
}
