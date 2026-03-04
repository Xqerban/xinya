package com.xinya.dtx.pro.controller;

import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.pro.dto.ProHistoryPageDto;
import com.xinya.dtx.pro.dto.ProQuestionListDto;
import com.xinya.dtx.pro.dto.ProSubmitRequest;
import com.xinya.dtx.pro.dto.ProSubmitResultDto;
import com.xinya.dtx.pro.dto.SymptomTrendResponseDto;
import com.xinya.dtx.pro.service.ProService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pro")
@RequiredArgsConstructor
public class ProController {

    private final ProService proService;

    @GetMapping("/questions")
    public ApiResponse<ProQuestionListDto> getTodayQuestions(@RequestParam("patientId") String patientId) {
        try {
            ProQuestionListDto dto = proService.getTodayQuestions(patientId);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @PostMapping("/submit")
    public ApiResponse<ProSubmitResultDto> submit(@Valid @RequestBody ProSubmitRequest request) {
        try {
            ProSubmitResultDto result = proService.submit(request);
            return ApiResponse.success("打卡提交成功", result);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        } catch (IllegalStateException e) {
            return ApiResponse.error(409, e.getMessage());
        }
    }

    @GetMapping("/history")
    public ApiResponse<ProHistoryPageDto> history(@RequestParam("patientId") String patientId,
                                                  @RequestParam(value = "startDate", required = false) String startDate,
                                                  @RequestParam(value = "endDate", required = false) String endDate,
                                                  @RequestParam(value = "page", required = false) Integer page,
                                                  @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        try {
            ProHistoryPageDto dto = proService.getHistory(patientId, startDate, endDate, page, pageSize);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }

    @GetMapping("/symptom-trend")
    public ApiResponse<SymptomTrendResponseDto> symptomTrend(
            @RequestParam("patientId") String patientId,
            @RequestParam("questionId") String questionId,
            @RequestParam(value = "days", required = false) Integer days) {
        try {
            SymptomTrendResponseDto dto = proService.getSymptomTrend(patientId, questionId, days);
            return ApiResponse.success(dto);
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, "患者不存在");
        }
    }
}

