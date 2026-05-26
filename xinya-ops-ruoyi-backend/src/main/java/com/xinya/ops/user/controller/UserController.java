package com.xinya.ops.user.controller;

import com.xinya.ops.common.domain.PageResult;
import com.xinya.ops.common.domain.R;
import com.xinya.ops.user.dto.CreateUserRequest;
import com.xinya.ops.user.dto.UpdateUserRequest;
import com.xinya.ops.user.dto.UserDto;
import com.xinya.ops.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<PageResult<UserDto>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return R.ok(userService.listUsers(role, page, pageSize));
    }

    @PostMapping
    public R<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            return R.ok(userService.createUser(request));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public R<UserDto> updateUser(@PathVariable String id,
                                 @Valid @RequestBody UpdateUserRequest request) {
        try {
            return R.ok(userService.updateUser(id, request));
        } catch (IllegalArgumentException e) {
            return R.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{id}/deactivate")
    public R<Void> deactivateUser(@PathVariable String id) {
        return userService.deactivateUser(id) ? R.ok() : R.fail(404, "用户不存在");
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteUser(@PathVariable String id) {
        return userService.deleteUser(id) ? R.ok() : R.fail(404, "用户不存在");
    }
}
