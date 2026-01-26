package com.xinya.dtx.service;

import com.xinya.dtx.dto.PatientDto;
import com.xinya.dtx.entity.ClinicalStage;
import com.xinya.dtx.entity.Patient;
import com.xinya.dtx.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {
    
    private final PatientRepository patientRepository;
    
    public Optional<PatientDto> getPatient(String id) {
        return patientRepository.findById(id)
            .map(PatientDto::fromEntity);
    }
    
    public List<PatientDto> getAllPatients() {
        return patientRepository.findAll().stream()
            .map(PatientDto::fromEntity)
            .toList();
    }
    
    @Transactional
    public PatientDto createPatient(String name, String roomNumber, LocalDate admissionDate) {
        Patient patient = Patient.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .roomNumber(roomNumber)
            .admissionDate(admissionDate)
            .stage(ClinicalStage.ADMISSION)
            .psychEnergy(50)
            .treeLevel(1)
            .build();
        
        patient = patientRepository.save(patient);
        return PatientDto.fromEntity(patient);
    }
    
    @Transactional
    public Optional<PatientDto> updateStage(String patientId, ClinicalStage targetStage) {
        return patientRepository.findById(patientId)
            .map(patient -> {
                if (patient.getStage().canTransitionTo(targetStage)) {
                    patient.setStage(targetStage);
                    return PatientDto.fromEntity(patientRepository.save(patient));
                }
                throw new IllegalStateException("无法从" + patient.getStage().getDisplayName() + 
                    "流转到" + targetStage.getDisplayName());
            });
    }
    
    @Transactional
    public void updatePsychEnergy(String patientId, int delta) {
        patientRepository.findById(patientId).ifPresent(patient -> {
            int newEnergy = Math.max(0, Math.min(100, patient.getPsychEnergy() + delta));
            patient.setPsychEnergy(newEnergy);
            patientRepository.save(patient);
        });
    }
    
    public List<PatientDto> getPatientsByStage(ClinicalStage stage) {
        return patientRepository.findByStage(stage).stream()
            .map(PatientDto::fromEntity)
            .toList();
    }
}
