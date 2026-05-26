package com.xinya.ops.stats.controller;

import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.*;

/**
 * 统计数据接口（全部代理到 xinya-ruoyi-backend /internal/stats/*）
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ClinicalApiClient clinicalApiClient;

    @GetMapping("/overview")
    public R<Object> getOverview() {
        R<Object> resp = clinicalApiClient.get("/internal/stats/overview",
                new ParameterizedTypeReference<R<Object>>() {});
        return resp != null && resp.isOk() ? R.ok(resp.getData()) : R.fail(502, "获取临床数据失败");
    }

    @GetMapping("/psych-distribution")
    public R<Object> getPsychDistribution() {
        R<Object> resp = clinicalApiClient.get("/internal/stats/psych-distribution",
                new ParameterizedTypeReference<R<Object>>() {});
        return resp != null && resp.isOk() ? R.ok(resp.getData()) : R.fail(502, "获取临床数据失败");
    }

    @GetMapping("/symptom-heatmap")
    public R<Object> getSymptomHeatmap(@RequestParam(required = false) Integer days) {
        String path = "/internal/stats/symptom-heatmap" + (days != null ? "?days=" + days : "");
        R<Object> resp = clinicalApiClient.get(path, new ParameterizedTypeReference<R<Object>>() {});
        return resp != null && resp.isOk() ? R.ok(resp.getData()) : R.fail(502, "获取临床数据失败");
    }
}
