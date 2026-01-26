package com.xinya.dtx.repository;

import com.xinya.dtx.entity.ClinicalStage;
import com.xinya.dtx.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, String> {
    
    List<Patient> findByStage(ClinicalStage stage);
    
    List<Patient> findByRoomNumber(String roomNumber);
}
