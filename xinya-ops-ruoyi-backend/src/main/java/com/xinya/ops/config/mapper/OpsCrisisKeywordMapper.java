package com.xinya.ops.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.ops.config.entity.OpsCrisisKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OpsCrisisKeywordMapper extends BaseMapper<OpsCrisisKeyword> {

    @Select("SELECT * FROM crisis_keywords WHERE is_active = 1 ORDER BY crisis_level DESC, keyword ASC")
    List<OpsCrisisKeyword> findAllActive();

    @Select("SELECT COUNT(*) > 0 FROM crisis_keywords WHERE keyword = #{keyword}")
    boolean existsByKeyword(@Param("keyword") String keyword);

    @Update("UPDATE crisis_keywords SET is_active = 0 WHERE id = #{id}")
    void deactivate(@Param("id") Long id);
}
