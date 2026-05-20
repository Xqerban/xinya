package com.xinya.business.pro.service;

import com.xinya.business.pro.dto.*;

import java.util.List;

public interface ProService {
    List<ProQuestionDto> getQuestions(String stage);
    ProRecordResultDto submitProRecord(SubmitProRecordRequest request);
    ProRecordResultDto getProResult(String patientId, String recordDate);
    List<ProQuestionDto> getActiveQuestions();
}
