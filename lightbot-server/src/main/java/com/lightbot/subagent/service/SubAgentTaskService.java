package com.lightbot.subagent.service;

import org.springframework.ai.chat.model.ToolContext;

import java.util.List;

/** SubAgent 批次编排服务。 */
public interface SubAgentTaskService {

    /** 执行委派工具请求。 */
    String delegate(String toolInput, ToolContext toolContext, List<Long> boundSubAgentIds);

    /** 查询已委派任务或批次。 */
    String query(String toolInput, ToolContext toolContext);

    /** 请求取消已委派任务或批次。 */
    String cancel(String toolInput, ToolContext toolContext);
}
