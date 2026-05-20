package com.xinya.business.alerts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.alerts.entity.Alert;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    @Select("SELECT COUNT(1) FROM alerts WHERE resolved = false")
    long countByResolvedFalse();

    @Select("SELECT COUNT(1) FROM alerts WHERE resolved = false")
    long countAllUnresolved();

    @Select("SELECT COUNT(1) FROM alerts WHERE patient_id = #{patientId} AND resolved = false")
    long countByPatientIdAndResolvedFalse(String patientId);

    @Select("SELECT COUNT(1) FROM alerts WHERE level = #{level}")
    long countBySeverity(String level);

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS count FROM alerts " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE(created_at) ORDER BY date ASC")
    List<Map<String, Object>> countLast7Days();

    @Update("UPDATE alerts SET resolved = true, resolved_by = #{resolvedBy}, " +
            "resolved_note = #{note}, resolved_at = #{now} WHERE id = #{id} AND resolved = false")
    int resolve(@Param("id") String id,
                @Param("resolvedBy") String resolvedBy,
                @Param("note") String note,
                @Param("now") LocalDateTime now);

    IPage<Alert> findByFilters(Page<Alert> page,
                               @Param("resolved") Boolean resolved,
                               @Param("level") String level,
                               @Param("patientId") String patientId);

    IPage<Alert> pageAlerts(Page<Alert> page,
                            @Param("patientId") String patientId,
                            @Param("resolved") Boolean resolved);

    @Select("SELECT * FROM alerts WHERE resolved = false ORDER BY created_at DESC LIMIT #{limit}")
    List<Alert> findLatestUnresolved(@Param("limit") int limit);
}
