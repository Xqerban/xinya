package com.xinya.dtx.agent.mapper;

import com.xinya.dtx.agent.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMapper extends JpaRepository<Conversation, Long> {

    /** 分页查询患者某智能体的对话（历史记录页） */
    Page<Conversation> findByPatientIdAndAgentTypeOrderByCreatedAtDesc(
            String patientId, String agentType, Pageable pageable);

    /** 分页查询患者全部对话（不区分智能体类型） */
    Page<Conversation> findByPatientIdOrderByCreatedAtDesc(String patientId, Pageable pageable);

    /** 查询某会话的全部消息（按时间升序，供 AI 上下文拼装） */
    List<Conversation> findByPatientIdAndSessionIdOrderByCreatedAtAsc(
            String patientId, String sessionId);

    /** 获取最近 N 条对话（供 AI 上下文窗口构建，limit 由 Pageable 控制） */
    @Query("SELECT c FROM Conversation c WHERE c.patientId = :patientId " +
           "AND c.agentType = :agentType ORDER BY c.createdAt DESC")
    List<Conversation> findRecentByPatientIdAndAgentType(
            @Param("patientId") String patientId,
            @Param("agentType") String agentType,
            Pageable pageable);

    /** 按会话 ID 分页查询 */
    Page<Conversation> findByPatientIdAndSessionId(
            String patientId, String sessionId, Pageable pageable);

    /** 统计患者某类智能体的消息总数 */
    long countByPatientIdAndAgentType(String patientId, String agentType);

    /** 统计患者触发危机预警的消息数 */
    long countByPatientIdAndCrisisAlertTrue(String patientId);

    /** 查询患者最近一次触发危机预警的会话ID */
    @Query("SELECT c.sessionId FROM Conversation c WHERE c.patientId = :patientId " +
           "AND c.crisisAlert = true ORDER BY c.createdAt DESC")
    List<String> findLatestCrisisSessionId(@Param("patientId") String patientId,
                                           Pageable pageable);

    /** 查询指定会话最新一条 AI 回复（用于危机处理后的上下文判断） */
    @Query("SELECT c FROM Conversation c WHERE c.sessionId = :sessionId " +
           "AND c.isFromUser = false ORDER BY c.createdAt DESC")
    List<Conversation> findLatestAiReplyInSession(@Param("sessionId") String sessionId,
                                                  Pageable pageable);
}
