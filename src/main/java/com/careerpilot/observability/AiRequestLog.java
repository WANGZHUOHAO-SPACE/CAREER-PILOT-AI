package com.careerpilot.observability;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_request_log")
public class AiRequestLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private String conversationId;
    private String modelName;
    private String provider;
    private String promptVersion;
    private String status;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private Long timeToFirstTokenMs;
    private Long streamDurationMs;
    private Integer outputChunkCount;
    private Boolean cancelled;
    private Integer toolCallCount;
    private Boolean ragUsed;
    private String errorType;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Long getLatencyMs() { return latencyMs; }
    public Long getTimeToFirstTokenMs() { return timeToFirstTokenMs; }
    public void setTimeToFirstTokenMs(Long value) { timeToFirstTokenMs = value; }
    public Long getStreamDurationMs() { return streamDurationMs; }
    public void setStreamDurationMs(Long value) { streamDurationMs = value; }
    public Integer getOutputChunkCount() { return outputChunkCount; }
    public void setOutputChunkCount(Integer value) { outputChunkCount = value; }
    public Boolean getCancelled() { return cancelled; }
    public void setCancelled(Boolean value) { cancelled = value; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    public Boolean getRagUsed() { return ragUsed; }
    public void setRagUsed(Boolean ragUsed) { this.ragUsed = ragUsed; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
