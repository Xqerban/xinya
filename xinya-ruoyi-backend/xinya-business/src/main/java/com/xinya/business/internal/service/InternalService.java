package com.xinya.business.internal.service;

import com.xinya.business.internal.dto.*;

public interface InternalService {
    Object syncUser(SyncUserRequest request);
    Object syncCrisisKeyword(SyncCrisisKeywordRequest request);
    Object syncProQuestion(SyncProQuestionRequest request);
}
