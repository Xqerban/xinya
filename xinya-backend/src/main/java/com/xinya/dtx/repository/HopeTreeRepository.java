package com.xinya.dtx.repository;

import com.xinya.dtx.entity.HopeTreeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HopeTreeRepository extends JpaRepository<HopeTreeProgress, Long> {
    
    Optional<HopeTreeProgress> findByPatientId(String patientId);
}
