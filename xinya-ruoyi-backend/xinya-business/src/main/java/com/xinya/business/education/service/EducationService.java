package com.xinya.business.education.service;

import com.xinya.business.education.dto.*;
import com.xinya.common.core.domain.PageResult;

import java.util.List;

public interface EducationService {
    PageResult<EducationContentDto> listContents(Integer page, Integer pageSize, String category, String stage);
    EducationContentDto getContentById(String id);
    EducationProgressDto markProgress(String patientId, String contentId);
    List<EducationProgressDto> getPatientProgress(String patientId);
}
