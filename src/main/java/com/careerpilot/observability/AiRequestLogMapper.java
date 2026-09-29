package com.careerpilot.observability;

import java.time.LocalDateTime;
import java.util.Map;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiRequestLogMapper extends BaseMapper<AiRequestLog> {
    @Select("""
            SELECT COUNT(*) AS total_requests,
                   COALESCE(SUM(status = 'SUCCESS'), 0) AS successful_requests,
                   COALESCE(SUM(status = 'FAILED'), 0) AS failed_requests,
                   SUM(total_tokens) AS total_tokens,
                   COALESCE(AVG(latency_ms), 0) AS average_latency_ms,
                   COALESCE(SUM(tool_call_count), 0) AS tool_calls,
                   COALESCE(SUM(rag_used = 1), 0) AS rag_requests
            FROM ai_request_log WHERE user_id = #{userId} AND created_at >= #{since}
            """)
    Map<String, Object> summary(@Param("userId") long userId, @Param("since") LocalDateTime since);
}
