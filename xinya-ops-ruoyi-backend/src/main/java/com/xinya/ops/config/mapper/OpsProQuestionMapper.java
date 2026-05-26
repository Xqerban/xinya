package com.xinya.ops.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinya.ops.config.entity.OpsProQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OpsProQuestionMapper extends BaseMapper<OpsProQuestion> {

    @Select("<script>SELECT * FROM pro_questions " +
            "<where><if test='stage != null'>AND stage = #{stage}</if></where>" +
            " ORDER BY sort_order ASC</script>")
    List<OpsProQuestion> findByStage(@Param("stage") String stage);

    @Select("SELECT * FROM pro_questions ORDER BY sort_order ASC")
    List<OpsProQuestion> findAllOrdered();
}
