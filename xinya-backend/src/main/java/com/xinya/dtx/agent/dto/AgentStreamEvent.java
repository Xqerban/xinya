package com.xinya.dtx.agent.dto;

/**
 * SSE event for agent streaming.
 * eventName: start/delta/done/error
 * dataJson: JSON string (one line) to send as "data:" payload
 */
public record AgentStreamEvent(String eventName, String dataJson) {
}

