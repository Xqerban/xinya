package com.xinya.dtx.education.service;

import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.education.dto.*;
import com.xinya.dtx.system.entity.EducationContent;

public interface EducationService {

    PageResult<EducationContentDto> getContents(String stage, String category, String contentType,
                                                 String keyword, Integer page, Integer pageSize);

    EducationContentDto getContentDetail(String id);

    EducationProgressResponse recordProgress(EducationProgressRequest request);

    PatientProgressSummaryDto getPatientProgress(String patientId);

    EducationContentDto createContent(CreateEducationContentRequest request);

    EducationContentDto updateContent(String id, UpdateEducationContentRequest request);

    void deactivateContent(String id);
}
