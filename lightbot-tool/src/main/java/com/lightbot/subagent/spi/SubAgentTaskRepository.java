package com.lightbot.subagent.spi;

import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.SubAgentTaskBatch;
import com.lightbot.entity.SubAgentTaskEvent;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/** SubAgent 批次和任务持久化 SPI。 */
public interface SubAgentTaskRepository {

    SubAgentTaskBatch findBatch(String batchId);

    void saveBatch(SubAgentTaskBatch batch);

    SubAgentRun findTask(String taskId);

    List<SubAgentRun> findTasks(String batchId);

    void saveTask(SubAgentRun task);

    int requestCancelTask(String taskId);

    int requestCancelBatch(String batchId);

    /** 请求取消一个父请求下所有运行中的任务（对话停止时连带取消子任务）。 */
    int requestCancelByParentRequestId(String parentRequestId);

    /** 分页查询一个会话下的任务运行记录。 */
    Page<SubAgentRun> pageTasks(Long parentSessionId, String batchId, int pageNum, int pageSize);

    void saveTaskEvent(SubAgentTaskEvent event);

    List<SubAgentTaskEvent> findTaskEvents(String taskId, Long cursor, int limit);

    SubAgentTaskEvent findLatestTaskEvent(String taskId);
}
