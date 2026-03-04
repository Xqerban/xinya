package com.xinya.dtx.alerts.service;

import com.xinya.dtx.alerts.dto.AlertDto;
import com.xinya.dtx.alerts.dto.AlertListResponse;
import com.xinya.dtx.alerts.dto.CreateAlertRequest;
import com.xinya.dtx.alerts.dto.ResolveAlertRequest;

public interface AlertService {

    AlertListResponse list(Boolean resolved, String level, String patientId, Integer page, Integer pageSize);

    AlertDto resolve(String id, ResolveAlertRequest request);

    AlertDto create(CreateAlertRequest request);
}

