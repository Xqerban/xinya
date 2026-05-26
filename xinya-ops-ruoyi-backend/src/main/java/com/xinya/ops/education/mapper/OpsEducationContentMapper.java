package com.xinya.ops.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.ops.education.entity.OpsEducationContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OpsEducationContentMapper extends BaseMapper<OpsEducationContent> {

    IPage<OpsEducationContent> findByFilters(@Param("page") Page<OpsEducationContent> page,
                                              @Param("stage") String stage,
                                              @Param("category") String category,
                                              @Param("contentType") String contentType,
                                              @Param("keyword") String keyword);
}
