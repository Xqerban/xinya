package com.xinya.dtx.admin.controller;

import com.xinya.dtx.admin.dto.*;
import com.xinya.dtx.admin.service.AdminService;
import com.xinya.dtx.common.response.ApiResponse;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.user.dto.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 12.1 用户管理 - 获取用户列表
     */
    @GetMapping("/users")
    public ApiResponse<PageResult<UserDto>> listUsers(@RequestParam(required = false) String role,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false, name = "pageSize") Integer pageSize) {
        PageResult<UserDto> result = adminService.listUsers(role, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 12.2 用户管理 - 创建用户
     */
    @PostMapping("/users")
    public ApiResponse<UserDto> createUser(@RequestBody @Valid AdminCreateUserRequest request) {
        try {
            UserDto user = adminService.createUser(request);
            return ApiResponse.success(user);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 12.3 用户管理 - 修改用户
     */
    @PutMapping("/users/{id}")
    public ApiResponse<UserDto> updateUser(@PathVariable("id") String userId,
                                           @RequestBody @Valid AdminUpdateUserRequest request) {
        try {
            UserDto user = adminService.updateUser(userId, request);
            return ApiResponse.success(user);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * Admin 注销用户（逻辑停用）
     */
    @PostMapping("/users/{id}/deactivate")
    public ApiResponse<Void> deactivateUser(@PathVariable("id") String userId) {
        boolean ok = adminService.deactivateUser(userId);
        if (!ok) {
            return ApiResponse.error(404, "用户不存在或已注销");
        }
        return ApiResponse.success("用户已注销", null);
    }

    /**
     * Admin 删除用户（物理删除）
     */
    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable("id") String userId) {
        boolean ok = adminService.deleteUser(userId);
        if (!ok) {
            return ApiResponse.error(404, "用户不存在或已删除");
        }
        return ApiResponse.success("用户已删除", null);
    }

    /**
     * 12.4 系统配置 - 获取 PRO 问卷列表
     */
    @GetMapping("/pro-questions")
    public ApiResponse<List<?>> getProQuestions(@RequestParam(required = false) String stage) {
        List<?> list = adminService.getProQuestions(stage);
        return ApiResponse.success(list);
    }

    /**
     * 12.5 系统配置 - 危机关键词管理
     */
    @GetMapping("/crisis-keywords")
    public ApiResponse<List<CrisisKeywordDto>> listCrisisKeywords() {
        return ApiResponse.success(adminService.listCrisisKeywords());
    }

    @PostMapping("/crisis-keywords")
    public ApiResponse<CrisisKeywordDto> createCrisisKeyword(@RequestBody @Valid CreateCrisisKeywordRequest request) {
        try {
            return ApiResponse.success(adminService.createCrisisKeyword(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/crisis-keywords/{id}")
    public ApiResponse<Void> deleteCrisisKeyword(@PathVariable("id") Long id) {
        adminService.deleteCrisisKeyword(id);
        return ApiResponse.success(null);
    }

    /**
     * 12.6 审计日志查询
     */
    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AuditLogDto>> listAuditLogs(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        PageResult<AuditLogDto> result = adminService.listAuditLogs(
                userId, action, targetType, targetId, startDate, endDate, page, pageSize);
        return ApiResponse.success(result);
    }
}

