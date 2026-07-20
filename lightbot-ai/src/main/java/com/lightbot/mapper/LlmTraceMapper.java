package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.LlmTrace;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM调用链追踪 Mapper
 *
 * @author finch
 * @since 2026-05-23
 */
@Mapper
public interface LlmTraceMapper extends BaseMapper<LlmTrace> {

    /**
     * SQL 聚合统计（替代全量加载到内存的 Java Stream 聚合）
     *
     * @param traceSource 来源类型（chat/workflow/null 表示全部）
     * @param startTime   起始时间
     * @return 聚合结果（total_count, total_tokens, avg_duration_ms, total_tool_calls）
     */
    @Select("""
            SELECT
                COUNT(*) AS total_count,
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COALESCE(AVG(total_duration_ms), 0) AS avg_duration_ms,
                COALESCE(SUM(tool_call_count), 0) AS total_tool_calls
            FROM llm_trace
            WHERE (#{traceSource} IS NULL AND (trace_source = 'chat' OR trace_source = 'workflow' OR trace_source IS NULL))
               OR (#{traceSource} IS NOT NULL AND trace_source = #{traceSource})
            """)
    Map<String, Object> aggregateOverview(@Param("traceSource") String traceSource);

    /**
     * 物理删除早于阈值的调用链记录（TTL 清理）
     * <p>llm_trace 表无 deleted 列、不走 @TableLogic，直接 DELETE 释放空间</p>
     *
     * @param threshold 阈值时间（早于此时间的记录将被删除）
     * @return 受影响行数
     */
    @Delete("DELETE FROM llm_trace WHERE create_time < #{threshold}")
    int deleteByCreateTimeBefore(@Param("threshold") LocalDateTime threshold);
}
