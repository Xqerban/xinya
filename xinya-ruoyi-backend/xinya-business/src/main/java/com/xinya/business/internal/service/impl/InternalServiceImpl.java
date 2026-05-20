package com.xinya.business.internal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinya.business.agent.entity.CrisisKeyword;
import com.xinya.business.agent.mapper.CrisisKeywordMapper;
import com.xinya.business.internal.dto.*;
import com.xinya.business.internal.service.InternalService;
import com.xinya.business.pro.entity.ProQuestion;
import com.xinya.business.pro.mapper.ProQuestionMapper;
import com.xinya.business.user.entity.User;
import com.xinya.business.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalServiceImpl implements InternalService {

    private final UserMapper userMapper;
    private final CrisisKeywordMapper crisisKeywordMapper;
    private final ProQuestionMapper proQuestionMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public Object syncUser(SyncUserRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            user = User.builder()
                    .id(request.getId() != null ? request.getId() : UUID.randomUUID().toString())
                    .username(request.getUsername())
                    .passwordHash(request.getPasswordHash() != null
                            ? request.getPasswordHash()
                            : passwordEncoder.encode("changeme"))
                    .displayName(request.getDisplayName())
                    .role(request.getRole())
                    .phone(request.getPhone())
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .build();
            userMapper.insert(user);
        } else {
            if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
            if (request.getRole() != null) user.setRole(request.getRole());
            if (request.getPhone() != null) user.setPhone(request.getPhone());
            if (request.getEnabled() != null) user.setEnabled(request.getEnabled());
            userMapper.updateById(user);
        }
        return Map.of("id", user.getId(), "username", user.getUsername());
    }

    @Override
    @Transactional
    public Object syncCrisisKeyword(SyncCrisisKeywordRequest request) {
        LambdaQueryWrapper<CrisisKeyword> wrapper = new LambdaQueryWrapper<CrisisKeyword>()
                .eq(CrisisKeyword::getKeyword, request.getKeyword())
                .last("LIMIT 1");
        CrisisKeyword keyword = crisisKeywordMapper.selectOne(wrapper);
        if (keyword == null) {
            keyword = CrisisKeyword.builder()
                    .keyword(request.getKeyword())
                    .crisisLevel(request.getCrisisLevel())
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .build();
            crisisKeywordMapper.insert(keyword);
        } else {
            if (request.getCrisisLevel() != null) keyword.setCrisisLevel(request.getCrisisLevel());
            if (request.getIsActive() != null) keyword.setIsActive(request.getIsActive());
            crisisKeywordMapper.updateById(keyword);
        }
        return Map.of("id", keyword.getId(), "keyword", keyword.getKeyword());
    }

    @Override
    @Transactional
    public Object syncProQuestion(SyncProQuestionRequest request) {
        ProQuestion question = request.getId() != null ? proQuestionMapper.selectById(request.getId()) : null;
        if (question == null) {
            question = ProQuestion.builder()
                    .id(request.getId() != null ? request.getId() : UUID.randomUUID().toString())
                    .stage(request.getStage())
                    .title(request.getTitle())
                    .type(request.getType())
                    .options(request.getOptions())
                    .scaleMin(request.getScaleMin())
                    .scaleMax(request.getScaleMax())
                    .minLabel(request.getMinLabel())
                    .maxLabel(request.getMaxLabel())
                    .symptomKey(request.getSymptomKey())
                    .sortOrder(request.getSortOrder())
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .build();
            proQuestionMapper.insert(question);
        } else {
            if (request.getTitle() != null) question.setTitle(request.getTitle());
            if (request.getIsActive() != null) question.setIsActive(request.getIsActive());
            proQuestionMapper.updateById(question);
        }
        return Map.of("id", question.getId());
    }
}
