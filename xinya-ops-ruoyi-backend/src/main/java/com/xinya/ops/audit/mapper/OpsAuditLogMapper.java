package com.xinya.ops.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.ops.audit.entity.OpsAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OpsAuditLogMapper extends BaseMapper<OpsAuditLog> {

    IPage<OpsAuditLog> findByFilters(@Param("page") Page<OpsAuditLog> page,
                                     @Param("operatorId") String operatorId,
                                     @Param("action") String action,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") String targetId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);
}
