package com.xinya.dtx.service;

import com.xinya.dtx.dto.PatientDto;
import com.xinya.dtx.entity.ClinicalStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClinicalPathService {
    
    private final PatientService patientService;
    
    public Optional<String> getCurrentStage(String patientId) {
        return patientService.getPatient(patientId)
            .map(PatientDto::getStage);
    }
    
    public Optional<PatientDto> transitionToNext(String patientId) {
        return patientService.getPatient(patientId)
            .flatMap(patient -> {
                ClinicalStage current = ClinicalStage.valueOf(patient.getStage());
                ClinicalStage next = current.next();
                if (next != null) {
                    return patientService.updateStage(patientId, next);
                }
                return Optional.empty();
            });
    }
    
    public Optional<PatientDto> transitionToStage(String patientId, String targetStageName) {
        try {
            ClinicalStage targetStage = ClinicalStage.valueOf(targetStageName.toUpperCase());
            return patientService.updateStage(patientId, targetStage);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }
}
