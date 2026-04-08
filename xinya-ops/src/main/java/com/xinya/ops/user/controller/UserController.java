package com.xinya.ops.user.controller;

import com.xinya.ops.common.response.ApiResponse;
import com.xinya.ops.common.response.PageResult;
import com.xinya.ops.user.dto.CreateUserRequest;
import com.xinya.ops.user.dto.UpdateUserRequest;
import com.xinya.ops.user.dto.UserDto;
import com.xinya.ops.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<UserDto>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(userService.listUsers(role, page, pageSize));
    }

    @PostMapping
    public ApiResponse<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            return ApiResponse.success(userService.createUser(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDto> updateUser(@PathVariable String id,
                                           @Valid @RequestBody UpdateUserRequest request) {
        try {
            return ApiResponse.success(userService.updateUser(id, request));
        } catch (EntityNotFoundException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<Void> deactivateUser(@PathVariable String id) {
        boolean ok = userService.deactivateUser(id);
        return ok ? ApiResponse.success("用户已停用", null) : ApiResponse.error(404, "用户不存在");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        boolean ok = userService.deleteUser(id);
        return ok ? ApiResponse.success("用户已删除", null) : ApiResponse.error(404, "用户不存在");
    }
}
