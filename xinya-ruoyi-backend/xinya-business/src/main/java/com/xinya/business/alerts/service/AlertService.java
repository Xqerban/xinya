package com.xinya.business.alerts.service;

import com.xinya.business.alerts.dto.*;
import com.xinya.common.core.domain.PageResult;

public interface AlertService {
    PageResult<AlertDto> listAlerts(Integer page, Integer pageSize, String patientId, Boolean resolved);
    AlertDto getAlertById(String id);
    AlertDto resolveAlert(String id, ResolveAlertRequest request);
    AlertDto createAlert(CreateAlertRequest request);
    long countUnresolvedByPatient(String patientId);
}
