package com.xinya.ops.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.ops.user.entity.OpUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OpUserMapper extends BaseMapper<OpUser> {

    IPage<OpUser> findByRoleOrAll(@Param("page") Page<OpUser> page,
                                  @Param("role") String role);

    @Update("UPDATE op_users SET enabled = 0 WHERE id = #{id}")
    int deactivate(@Param("id") String id);

    @Update("DELETE FROM op_users WHERE id = #{id}")
    int hardDelete(@Param("id") String id);
}
