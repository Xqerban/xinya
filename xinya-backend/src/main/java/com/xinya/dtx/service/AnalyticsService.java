package com.xinya.dtx.service;

import com.xinya.dtx.dto.DashboardDto;
import com.xinya.dtx.entity.ClinicalStage;
import com.xinya.dtx.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    
    private final PatientRepository patientRepository;
    
    public DashboardDto getDashboardOverview() {
        // 开发阶段返回Mock数据
        // 后续实现真实的数据分析逻辑
        long patientCount = patientRepository.count();
        
        if (patientCount == 0) {
            return DashboardDto.mockData();
        }
        
        // 统计各阶段患者数量
        Map<String, Integer> stageStats = new HashMap<>();
        for (ClinicalStage stage : ClinicalStage.values()) {
            int count = patientRepository.findByStage(stage).size();
            stageStats.put(stage.name(), count);
        }
        
        // 计算平均心理能量
        double avgEnergy = patientRepository.findAll().stream()
            .mapToInt(p -> p.getPsychEnergy())
            .average()
            .orElse(50.0);
        
        DashboardDto mockData = DashboardDto.mockData();
        mockData.setTotalPatients((int) patientCount);
        mockData.setPatientsByStage(stageStats);
        mockData.setAvgPsychEnergy(avgEnergy);
        
        return mockData;
    }
}
