package com.xinya.dtx.hopetree.service;

import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.hopetree.dto.GrowthHistoryItemDto;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowRequest;
import com.xinya.dtx.hopetree.dto.HopeTreeGrowResponse;
import com.xinya.dtx.patient.dto.HopeTreeDto;

public interface HopeTreeService {

    HopeTreeDto getState(String patientId);

    HopeTreeGrowResponse grow(HopeTreeGrowRequest request);

    PageResult<GrowthHistoryItemDto> getHistory(String patientId, Integer page, Integer pageSize);
}
