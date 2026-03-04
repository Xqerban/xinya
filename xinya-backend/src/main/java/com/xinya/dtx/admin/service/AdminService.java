package com.xinya.dtx.admin.service;

import com.xinya.dtx.admin.dto.*;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.user.dto.UserDto;

import java.util.List;

public interface AdminService {

    PageResult<UserDto> listUsers(String role, Integer page, Integer pageSize);

    UserDto createUser(AdminCreateUserRequest request);

    UserDto updateUser(String userId, AdminUpdateUserRequest request);

    /**
     * Admin 注销用户（逻辑停用）
     */
    boolean deactivateUser(String userId);

    /**
     * Admin 删除用户（物理删除）
     */
    boolean deleteUser(String userId);

    List<?> getProQuestions(String stage);

    List<CrisisKeywordDto> listCrisisKeywords();

    CrisisKeywordDto createCrisisKeyword(CreateCrisisKeywordRequest request);

    void deleteCrisisKeyword(Long id);

    PageResult<AuditLogDto> listAuditLogs(String userId, String action,
                                          String targetType, String targetId,
                                          String startDate, String endDate,
                                          Integer page, Integer pageSize);
}

