package com.xinya.dtx.controller;

import com.xinya.dtx.common.dto.ApiResponse;
import com.xinya.dtx.common.dto.ProSubmitResponse;
import com.xinya.dtx.entity.ProRecord;
import com.xinya.dtx.repository.ProRecordRepository;
import com.xinya.dtx.service.HopeTreeService;
import com.xinya.dtx.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pro")
@RequiredArgsConstructor
@Tag(name = "PRO数据采集", description = "患者每日打卡和症状记录")
public class ProController {
    
    private final HopeTreeService hopeTreeService;
    private final ProRecordRepository proRecordRepository;
    private final PatientService patientService;
    
    @PostMapping("/submit")
    @Operation(summary = "提交每日打卡数据")
    public ApiResponse<ProSubmitResponse> submitPro(@RequestBody ProSubmitRequest request) {
        LocalDate recordDate = request.getRecordDate() != null ? request.getRecordDate() : LocalDate.now();

        // 将每个答案保存到数据库
        if (request.getAnswers() != null) {
            for (ProAnswer answer : request.getAnswers()) {
                ProRecord record = ProRecord.builder()
                    .patientId(request.getPatientId())
                    .recordDate(recordDate)
                    .questionId(answer.getQuestionId())
                    .answer(answer.getAnswer() != null ? answer.getAnswer() : "")
                    .answerScore(answer.getScore() != null ? answer.getScore() : 0)
                    .build();
                proRecordRepository.save(record);
            }
        }

        // 增加心理能量（打卡奖励10点）
        patientService.updatePsychEnergy(request.getPatientId(), 10);

        // 给希望之树增加经验值
        hopeTreeService.grow(request.getPatientId(), "check_in", 20);
        
        ProSubmitResponse response = ProSubmitResponse.success();
        return ApiResponse.success("打卡提交成功", response);
    }
    
    @Data
    static class ProSubmitRequest {
        private String patientId;
        private LocalDate recordDate;
        private List<ProAnswer> answers;
    }
    
    @Data
    static class ProAnswer {
        private String questionId;
        private String answer;
        private Integer score;
    }
}
