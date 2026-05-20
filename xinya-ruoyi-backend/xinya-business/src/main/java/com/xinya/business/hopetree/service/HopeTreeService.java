package com.xinya.business.hopetree.service;

import com.xinya.business.hopetree.dto.HopeTreeDetailDto;
import com.xinya.business.hopetree.dto.HopeTreeGrowthHistoryDto;

import java.util.List;

public interface HopeTreeService {
    HopeTreeDetailDto getPatientTree(String patientId);
    void addGrowthExp(String patientId, int exp, String source, String note);
    List<HopeTreeGrowthHistoryDto> getGrowthHistory(String patientId, Integer limit);
    HopeTreeDetailDto dailyCheckIn(String patientId);
}
