package com.xinya.dtx.repository;

import com.xinya.dtx.entity.ProRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProRecordRepository extends JpaRepository<ProRecord, Long> {
    
    List<ProRecord> findByPatientIdOrderByRecordDateDesc(String patientId);
    
    List<ProRecord> findByPatientIdAndRecordDate(String patientId, LocalDate recordDate);
}
