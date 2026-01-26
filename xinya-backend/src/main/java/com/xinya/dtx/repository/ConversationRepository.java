package com.xinya.dtx.repository;

import com.xinya.dtx.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    List<Conversation> findByPatientIdOrderByCreatedAtDesc(String patientId);
    
    List<Conversation> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    
    List<Conversation> findByPatientIdAndAgentType(String patientId, String agentType);
}
