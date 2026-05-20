package com.xinya.business.internal.controller;

import com.xinya.business.internal.dto.*;
import com.xinya.business.internal.service.InternalService;
import com.xinya.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal API（运维专用）")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final InternalService internalService;

    @Operation(summary = "同步医护用户")
    @PostMapping("/sync/user")
    public R<Object> syncUser(@RequestBody SyncUserRequest request) {
        return R.ok(internalService.syncUser(request));
    }

    @Operation(summary = "同步危机关键词")
    @PostMapping("/sync/crisis-keyword")
    public R<Object> syncCrisisKeyword(@RequestBody SyncCrisisKeywordRequest request) {
        return R.ok(internalService.syncCrisisKeyword(request));
    }

    @Operation(summary = "同步 PRO 题目")
    @PostMapping("/sync/pro-question")
    public R<Object> syncProQuestion(@RequestBody SyncProQuestionRequest request) {
        return R.ok(internalService.syncProQuestion(request));
    }
}
