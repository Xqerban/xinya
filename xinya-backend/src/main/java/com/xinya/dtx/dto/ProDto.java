package com.xinya.dtx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
class ProSubmitRequest {
    private String patientId;
    private LocalDate recordDate;
    private List<ProAnswer> answers;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class ProAnswer {
    private String questionId;
    private String answer;
    private Integer score;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProSubmitResponse {
    private Boolean success;
    private Integer psychEnergyDelta;
    private String message;
    
    public static ProSubmitResponse success() {
        return ProSubmitResponse.builder()
            .success(true)
            .psychEnergyDelta(10)
            .message("打卡成功！您的希望之树获得了成长能量。")
            .build();
    }
}
