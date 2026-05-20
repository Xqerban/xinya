package com.xinya.business.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.business.education.entity.EducationContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EducationContentMapper extends BaseMapper<EducationContent> {

    @Select("SELECT COUNT(1) FROM education_contents WHERE is_active = true")
    long countByIsActiveTrue();

    @Update("UPDATE education_contents SET is_active = false WHERE id = #{id}")
    void deactivate(String id);

    IPage<EducationContent> findByFilters(Page<EducationContent> page,
                                          @Param("stage") String stage,
                                          @Param("category") String category,
                                          @Param("contentType") String contentType,
                                          @Param("keyword") String keyword);
}
