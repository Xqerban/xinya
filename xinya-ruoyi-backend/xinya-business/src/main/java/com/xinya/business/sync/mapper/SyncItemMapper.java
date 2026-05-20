package com.xinya.business.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.sync.entity.SyncItem;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface SyncItemMapper extends BaseMapper<SyncItem> {

    @Select("SELECT * FROM sync_items WHERE client_id = #{clientId} LIMIT 1")
    SyncItem findByClientId(String clientId);

    @Select("SELECT COUNT(1) FROM sync_items WHERE patient_id = #{patientId} AND status = #{status}")
    long countByPatientIdAndStatus(@Param("patientId") String patientId,
                                   @Param("status") String status);

    @Update("UPDATE sync_items SET status = 'success', server_id = #{serverId}, " +
            "processed_at = #{now} WHERE client_id = #{clientId}")
    void markSuccess(@Param("clientId") String clientId,
                     @Param("serverId") String serverId,
                     @Param("now") LocalDateTime now);

    @Update("UPDATE sync_items SET status = 'failed', error_code = #{errorCode}, " +
            "error_message = #{errorMessage}, processed_at = #{now} WHERE client_id = #{clientId}")
    void markFailed(@Param("clientId") String clientId,
                    @Param("errorCode") int errorCode,
                    @Param("errorMessage") String errorMessage,
                    @Param("now") LocalDateTime now);

    @Select("SELECT * FROM sync_items WHERE device_id = #{deviceId} AND patient_id = #{patientId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    IPage<SyncItem> findRecentByDevice(@Param("deviceId") String deviceId,
                                       @Param("patientId") String patientId,
                                       @Param("limit") int limit);
}
