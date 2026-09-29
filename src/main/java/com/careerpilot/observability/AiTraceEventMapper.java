package com.careerpilot.observability;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiTraceEventMapper extends BaseMapper<AiTraceEvent> {
    @Select("""
            SELECT event_name AS tool_name, COUNT(*) AS call_count,
                   COALESCE(SUM(status = 'SUCCESS'), 0) AS success_count,
                   COALESCE(SUM(status = 'FAILED'), 0) AS failure_count,
                   COALESCE(AVG(duration_ms), 0) AS avg_latency_ms
            FROM ai_trace_event
            WHERE user_id = #{userId} AND event_type = 'TOOL_CALL' AND created_at >= #{since}
            GROUP BY event_name ORDER BY call_count DESC, event_name
            """)
    List<Map<String, Object>> toolStatistics(@Param("userId") long userId,
            @Param("since") LocalDateTime since);
}
