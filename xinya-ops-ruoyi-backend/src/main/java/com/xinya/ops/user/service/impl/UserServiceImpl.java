package com.xinya.ops.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.domain.PageResult;
import com.xinya.ops.common.domain.R;
import com.xinya.ops.user.dto.CreateUserRequest;
import com.xinya.ops.user.dto.SyncUserPayload;
import com.xinya.ops.user.dto.UpdateUserRequest;
import com.xinya.ops.user.dto.UserDto;
import com.xinya.ops.user.entity.OpUser;
import com.xinya.ops.user.mapper.OpUserMapper;
import com.xinya.ops.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("NURSE", "DOCTOR", "ADMIN");

    private final OpUserMapper opUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final ClinicalApiClient clinicalApiClient;

    @Override
    public PageResult<UserDto> listUsers(String role, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize <= 0) ? 20 : pageSize;

        var userPage = opUserMapper.findByRoleOrAll(
                new Page<>(p, size),
                StringUtils.hasText(role) ? role : null);

        List<UserDto> list = userPage.getRecords().stream()
                .map(this::toDto).collect(Collectors.toList());

        return PageResult.<UserDto>builder()
                .list(list)
                .total(userPage.getTotal())
                .page(p)
                .pageSize(size)
                .build();
    }

    @Override
    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色: " + request.getRole());
        }
        long cnt = opUserMapper.selectCount(
                new LambdaQueryWrapper<OpUser>().eq(OpUser::getUsername, request.getUsername()));
        if (cnt > 0) throw new IllegalArgumentException("用户名已存在");

        if (StringUtils.hasText(request.getPhone())) {
            long phoneCnt = opUserMapper.selectCount(
                    new LambdaQueryWrapper<OpUser>().eq(OpUser::getPhone, request.getPhone()));
            if (phoneCnt > 0) throw new IllegalArgumentException("手机号已被占用");
        }

        String id = UUID.randomUUID().toString();
        String hash = passwordEncoder.encode(request.getPassword());

        OpUser user = OpUser.builder()
                .id(id)
                .username(request.getUsername())
                .passwordHash(hash)
                .displayName(request.getDisplayName())
                .role(request.getRole())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        opUserMapper.insert(user);

        if (!"ADMIN".equals(request.getRole())) {
            syncUserToClinical(user, hash);
        }
        return toDto(user);
    }

    @Override
    @Transactional
    public UserDto updateUser(String userId, UpdateUserRequest request) {
        OpUser user = opUserMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");

        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色: " + request.getRole());
        }
        if (StringUtils.hasText(request.getPhone())) {
            OpUser other = opUserMapper.selectOne(
                    new LambdaQueryWrapper<OpUser>().eq(OpUser::getPhone, request.getPhone()));
            if (other != null && !Objects.equals(other.getId(), userId)) {
                throw new IllegalArgumentException("手机号已被占用");
            }
        }

        user.setDisplayName(request.getDisplayName());
        user.setRole(request.getRole());
        user.setPhone(request.getPhone());
        if (request.getEnabled() != null) user.setEnabled(request.getEnabled());

        String hashToSync = user.getPasswordHash();
        if (StringUtils.hasText(request.getNewPassword())) {
            hashToSync = passwordEncoder.encode(request.getNewPassword());
            user.setPasswordHash(hashToSync);
        }
        opUserMapper.updateById(user);

        if (!"ADMIN".equals(user.getRole())) {
            syncUserToClinical(user, hashToSync);
        }
        return toDto(user);
    }

    @Override
    @Transactional
    public boolean deactivateUser(String userId) {
        int affected = opUserMapper.deactivate(userId);
        if (affected > 0) {
            OpUser u = opUserMapper.selectById(userId);
            if (u != null && !"ADMIN".equals(u.getRole())) syncUserToClinical(u, null);
        }
        return affected > 0;
    }

    @Override
    @Transactional
    public boolean deleteUser(String userId) {
        return opUserMapper.hardDelete(userId) > 0;
    }

    private void syncUserToClinical(OpUser user, String passwordHash) {
        try {
            SyncUserPayload payload = SyncUserPayload.builder()
                    .id(user.getId()).username(user.getUsername())
                    .displayName(user.getDisplayName()).role(user.getRole())
                    .phone(user.getPhone()).enabled(user.getEnabled())
                    .passwordHash(passwordHash).build();
            clinicalApiClient.post("/internal/users/sync", payload,
                    new ParameterizedTypeReference<R<Void>>() {});
        } catch (Exception e) {
            log.warn("同步用户到 xinya-ruoyi-backend 失败，ID={}，错误：{}", user.getId(), e.getMessage());
        }
    }

    private UserDto toDto(OpUser user) {
        return UserDto.builder()
                .id(user.getId()).username(user.getUsername())
                .displayName(user.getDisplayName()).role(user.getRole())
                .phone(user.getPhone()).enabled(user.getEnabled())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .build();
    }
}
