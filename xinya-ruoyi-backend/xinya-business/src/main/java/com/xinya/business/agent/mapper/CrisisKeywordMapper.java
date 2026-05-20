package com.xinya.business.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.agent.entity.CrisisKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CrisisKeywordMapper extends BaseMapper<CrisisKeyword> {

    @Select("SELECT * FROM crisis_keywords WHERE keyword = #{keyword} LIMIT 1")
    CrisisKeyword findByKeyword(String keyword);

    @Select("SELECT * FROM crisis_keywords WHERE is_active = true ORDER BY keyword ASC")
    List<CrisisKeyword> findAllActive();
}
