package com.xinya.ops.education.service;

import com.xinya.ops.common.response.PageResult;
import com.xinya.ops.education.dto.CreateEducationContentRequest;
import com.xinya.ops.education.dto.EducationContentDto;
import com.xinya.ops.education.dto.UpdateEducationContentRequest;

public interface EducationService {

    PageResult<EducationContentDto> listContents(String stage, String category,
            String contentType, String keyword, Integer page, Integer pageSize);

    EducationContentDto getContent(String id);

    EducationContentDto createContent(CreateEducationContentRequest request, String operatorId);

    EducationContentDto updateContent(String id, UpdateEducationContentRequest request);

    void deactivateContent(String id);
}
