package com.xinya.dtx.admin.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.dtx.admin.dto.*;
import com.xinya.dtx.admin.service.AdminService;
import com.xinya.dtx.common.response.PageResult;
import com.xinya.dtx.pro.entity.ProQuestion;
import com.xinya.dtx.pro.mapper.ProQuestionMapper;
import com.xinya.dtx.system.entity.AuditLog;
import com.xinya.dtx.system.entity.CrisisKeyword;
import com.xinya.dtx.system.mapper.AuditLogMapper;
import com.xinya.dtx.system.mapper.CrisisKeywordMapper;
import com.xinya.dtx.user.dto.UserDto;
import com.xinya.dtx.user.entity.User;
import com.xinya.dtx.user.mapper.UserMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Set<String> ALLOWED_ROLES = Set.of("NURSE", "DOCTOR", "ADMIN");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ProQuestionMapper proQuestionMapper;
    private final CrisisKeywordMapper crisisKeywordMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserDto> listUsers(String role, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p, size);

        Page<User> userPage;
        if (StringUtils.hasText(role)) {
            userPage = userMapper.findByRole(role, pageable);
        } else {
            userPage = userMapper.findAll(pageable);
        }

        List<UserDto> list = userPage.getContent().stream()
                .map(this::toUserDto)
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
    public UserDto createUser(AdminCreateUserRequest request) {
        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色类型: " + request.getRole());
        }
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (StringUtils.hasText(request.getPhone()) && userMapper.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("手机号已被占用");
        }
        String id = UUID.randomUUID().toString();
        String hash = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
                .id(id)
                .username(request.getUsername())
                .passwordHash(hash)
                .displayName(request.getDisplayName())
                .role(request.getRole())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        userMapper.save(user);
        return toUserDto(user);
    }

    @Override
    @Transactional
    public UserDto updateUser(String userId, AdminUpdateUserRequest request) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));

        if (!ALLOWED_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException("不支持的角色类型: " + request.getRole());
        }

        // 校验手机号唯一
        String newPhone = request.getPhone();
        if (StringUtils.hasText(newPhone)) {
            // 若手机号变更，需要确保新手机号未被其他用户占用
            userMapper.findByPhone(newPhone).ifPresent(other -> {
                if (!Objects.equals(other.getId(), user.getId())) {
                    throw new IllegalArgumentException("手机号已被占用");
                }
            });
        }

        user.setDisplayName(request.getDisplayName());
        user.setRole(request.getRole());
        user.setPhone(newPhone);
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        User saved = userMapper.save(user);
        return toUserDto(saved);
    }

    @Override
    @Transactional
    public boolean deactivateUser(String userId) {
        int affected = userMapper.deactivateUser(userId);
        return affected > 0;
    }

    @Override
    @Transactional
    public boolean deleteUser(String userId) {
        int affected = userMapper.hardDeleteById(userId);
        return affected > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<?> getProQuestions(String stage) {
        List<ProQuestion> questions;
        if (StringUtils.hasText(stage)) {
            questions = proQuestionMapper.findActiveByStage(stage);
        } else {
            questions = proQuestionMapper.findAllByOrderBySortOrderAsc();
        }
        // 简单返回题目配置列表，按 stage 分组由前端处理
        return questions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrisisKeywordDto> listCrisisKeywords() {
        return crisisKeywordMapper.findAllByOrderByCrisisLevelDescKeywordAsc().stream()
                .map(this::toCrisisDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CrisisKeywordDto createCrisisKeyword(CreateCrisisKeywordRequest request) {
        if (crisisKeywordMapper.existsByKeyword(request.getKeyword())) {
            throw new IllegalArgumentException("关键词已存在");
        }
        CrisisKeyword k = CrisisKeyword.builder()
                .keyword(request.getKeyword())
                .crisisLevel(request.getCrisisLevel())
                .isActive(true)
                .build();
        crisisKeywordMapper.save(k);
        return toCrisisDto(k);
    }

    @Override
    @Transactional
    public void deleteCrisisKeyword(Long id) {
        if (!crisisKeywordMapper.existsById(id)) {
            throw new EntityNotFoundException("关键词不存在");
        }
        crisisKeywordMapper.deactivate(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLogDto> listAuditLogs(String userId, String action,
                                                 String targetType, String targetId,
                                                 String startDate, String endDate,
                                                 Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 0 : page - 1;
        int size = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p, size);

        LocalDateTime start = null;
        LocalDateTime end = null;
        if (StringUtils.hasText(startDate)) {
            LocalDate d = LocalDate.parse(startDate, DATE_FORMATTER);
            start = d.atStartOfDay();
        }
        if (StringUtils.hasText(endDate)) {
            LocalDate d = LocalDate.parse(endDate, DATE_FORMATTER);
            end = d.plusDays(1).atStartOfDay().minusNanos(1);
        }

        Page<AuditLog> pageData = auditLogMapper.findByFilters(
                StringUtils.hasText(userId) ? userId : null,
                StringUtils.hasText(action) ? action : null,
                StringUtils.hasText(targetType) ? targetType : null,
                StringUtils.hasText(targetId) ? targetId : null,
                start, end, pageable);

        List<AuditLogDto> list = pageData.getContent().stream()
                .map(this::toAuditDto)
                .collect(Collectors.toList());

        return PageResult.<AuditLogDto>builder()
                .list(list)
                .total(pageData.getTotalElements())
                .page(p + 1)
                .pageSize(size)
                .build();
    }

    private UserDto toUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .phone(user.getPhone())
                .enabled(user.getEnabled())
                .build();
    }

    private CrisisKeywordDto toCrisisDto(CrisisKeyword k) {
        return CrisisKeywordDto.builder()
                .id(k.getId())
                .keyword(k.getKeyword())
                .crisisLevel(k.getCrisisLevel())
                .isActive(k.getIsActive())
                .createdBy(k.getCreatedBy())
                .createdAt(k.getCreatedAt() != null ? k.getCreatedAt().toString() : null)
                .build();
    }

    private AuditLogDto toAuditDto(AuditLog l) {
        return AuditLogDto.builder()
                .id(l.getId())
                .operatorId(l.getOperatorId())
                .operatorName(l.getOperatorName())
                .action(l.getAction())
                .targetType(l.getTargetType())
                .targetId(l.getTargetId())
                .detail(l.getDetail())
                .ipAddress(l.getIpAddress())
                .createdAt(l.getCreatedAt() != null ? l.getCreatedAt().toString() : null)
                .build();
    }
}

