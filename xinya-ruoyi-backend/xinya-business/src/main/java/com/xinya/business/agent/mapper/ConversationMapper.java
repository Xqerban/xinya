package com.xinya.business.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.agent.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("SELECT * FROM conversations WHERE patient_id = #{patientId} " +
            "AND agent_type = #{agentType} ORDER BY created_at DESC LIMIT #{limit}")
    List<Conversation> findRecentByPatientIdAndAgentType(@Param("patientId") String patientId,
                                                         @Param("agentType") String agentType,
                                                         @Param("limit") int limit);

    IPage<Conversation> findByPatientIdAndSessionId(Page<Conversation> page,
                                                     @Param("patientId") String patientId,
                                                     @Param("sessionId") String sessionId);

    IPage<Conversation> findByPatientIdAndAgentTypeOrderByCreatedAtDesc(Page<Conversation> page,
                                                                         @Param("patientId") String patientId,
                                                                         @Param("agentType") String agentType);

    IPage<Conversation> findByPatientIdOrderByCreatedAtDesc(Page<Conversation> page,
                                                            @Param("patientId") String patientId);
}
