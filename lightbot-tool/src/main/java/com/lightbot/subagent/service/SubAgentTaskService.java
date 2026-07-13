package com.lightbot.subagent.service;

import org.springframework.ai.chat.model.ToolContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.entity.SubAgentRun;

import java.util.List;

/** SubAgent 批次编排服务。 */
public interface SubAgentTaskService {

    /** 执行委派工具请求。 */
    String delegate(String toolInput, ToolContext toolContext, List<Long> boundSubAgentIds);

    /** 查询已委派任务或批次。 */
    String query(String toolInput, ToolContext toolContext);

    /** 请求取消已委派任务或批次。 */
    String cancel(String toolInput, ToolContext toolContext);

    /** 查询会话内的 SubAgent 任务列表。 */
    Page<SubAgentRun> pageRuns(Long sessionId, String batchId, int pageNum, int pageSize);

    /** 查询会话内的批次详情及其全部任务。 */
    java.util.Map<String, Object> getBatchDetail(String batchId, Long sessionId);

    /** 查询会话内的单任务详情。 */
    java.util.Map<String, Object> getTaskDetail(String taskId, Long sessionId);

    /** 取消会话内的一个批次。 */
    java.util.Map<String, Object> cancelBatch(String batchId, Long sessionId);

    /** 取消会话内的一个单任务。 */
    java.util.Map<String, Object> cancelTask(String taskId, Long sessionId);

    /** 连带取消一个父请求下所有运行中的子任务（对话停止时调用）。 */
    int cancelByParentRequestId(String requestId);

    /** 获取任务对应子线程消息快照。 */
    java.util.Map<String, Object> getTaskThreadDetail(String taskId, Long sessionId);

    /** 按事件 ID 游标获取任务运行事件。 */
    java.util.Map<String, Object> getTaskEvents(String taskId, Long sessionId, Long cursor, int limit);

    /** 获取会话侧栏所需的任务运行态摘要。 */
    List<java.util.Map<String, Object>> listRuntimeSummaries(Long sessionId, int limit);
}
