package com.xinya.ops.user.service.impl;

import com.xinya.ops.common.client.ClinicalApiClient;
import com.xinya.ops.common.response.ApiResponse;
import com.xinya.ops.common.response.PageResult;
import com.xinya.ops.user.dto.CreateUserRequest;
import com.xinya.ops.user.dto.SyncUserPayload;
import com.xinya.ops.user.dto.UpdateUserRequest;
import com.xinya.ops.user.dto.UserDto;
import com.xinya.ops.user.entity.OpUser;
import com.xinya.ops.user.mapper.OpUserMapper;
import com.xinya.ops.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    @Transactional
    public PageResult<UserDto> listUsers(String role, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 0 : page - 1;
        int size = (pageSize == null || pageSize <= 0) ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p, size);

        Page<OpUser> userPage = opUserMapper.findByRoleOrAll(
                StringUtils.hasText(role) ? role : null, pageable);

        List<UserDto> list = userPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResult.<UserDto>builder()
                .list(list)
                .total(userPage.getTotalElements())
                .page(p + 1)
                .pageSize(size)
                .build();
    }

    @Override
    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色: " + request.getRole());
        }
        if (opUserMapper.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (StringUtils.hasText(request.getPhone()) && opUserMapper.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("手机号已被占用");
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
        opUserMapper.save(user);

        // 同步到 xinya-backend（非 ADMIN 角色需要登录 clinical 端）
        if (!"ADMIN".equals(request.getRole())) {
            syncUserToClinical(user, hash);
        }

        return toDto(user);
    }

    @Override
    @Transactional
    public UserDto updateUser(String userId, UpdateUserRequest request) {
        OpUser user = opUserMapper.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色: " + request.getRole());
        }
        if (StringUtils.hasText(request.getPhone())) {
            opUserMapper.findByPhone(request.getPhone()).ifPresent(other -> {
                if (!Objects.equals(other.getId(), userId)) {
                    throw new IllegalArgumentException("手机号已被占用");
                }
            });
        }

        user.setDisplayName(request.getDisplayName());
        user.setRole(request.getRole());
        user.setPhone(request.getPhone());
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        String hashToSync = user.getPasswordHash();
        if (StringUtils.hasText(request.getNewPassword())) {
            hashToSync = passwordEncoder.encode(request.getNewPassword());
            user.setPasswordHash(hashToSync);
        }

        opUserMapper.save(user);

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
            opUserMapper.findById(userId).ifPresent(u -> {
                if (!"ADMIN".equals(u.getRole())) {
                    syncUserToClinical(u, null);
                }
            });
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
                    .id(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .role(user.getRole())
                    .phone(user.getPhone())
                    .enabled(user.getEnabled())
                    .passwordHash(passwordHash)
                    .build();
            clinicalApiClient.post(
                    "/internal/users/sync",
                    payload,
                    new ParameterizedTypeReference<ApiResponse<Void>>() {});
        } catch (Exception e) {
            log.warn("同步用户到 xinya-backend 失败，用户 ID={}，错误：{}", user.getId(), e.getMessage());
        }
    }

    private UserDto toDto(OpUser user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .phone(user.getPhone())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .build();
    }
}
