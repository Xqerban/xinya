package com.xinya.dtx.pro.service;

import com.xinya.dtx.pro.dto.ProHistoryPageDto;
import com.xinya.dtx.pro.dto.ProQuestionListDto;
import com.xinya.dtx.pro.dto.ProSubmitRequest;
import com.xinya.dtx.pro.dto.ProSubmitResultDto;
import com.xinya.dtx.pro.dto.SymptomTrendResponseDto;

public interface ProService {

    ProQuestionListDto getTodayQuestions(String patientId);

    ProSubmitResultDto submit(ProSubmitRequest request);

    ProHistoryPageDto getHistory(String patientId, String startDate, String endDate,
                                 Integer page, Integer pageSize);

    SymptomTrendResponseDto getSymptomTrend(String patientId, String questionId, Integer days);
}

