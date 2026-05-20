package com.xinya.business.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.business.pro.entity.ProQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProQuestionMapper extends BaseMapper<ProQuestion> {

    @Select("SELECT * FROM pro_questions WHERE is_active = true " +
            "AND (stage = #{stage} OR stage = 'ALL') ORDER BY sort_order ASC")
    List<ProQuestion> findActiveByStage(String stage);
}
