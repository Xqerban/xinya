package com.xinya.ops.user.service;

import com.xinya.ops.common.domain.PageResult;
import com.xinya.ops.user.dto.CreateUserRequest;
import com.xinya.ops.user.dto.UpdateUserRequest;
import com.xinya.ops.user.dto.UserDto;

public interface UserService {
    PageResult<UserDto> listUsers(String role, Integer page, Integer pageSize);
    UserDto createUser(CreateUserRequest request);
    UserDto updateUser(String userId, UpdateUserRequest request);
    boolean deactivateUser(String userId);
    boolean deleteUser(String userId);
}
