package com.lightbot.subagent.spi;

import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.SubAgentTaskBatch;
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

    /** 分页查询一个会话下的任务运行记录。 */
    Page<SubAgentRun> pageTasks(Long parentSessionId, String batchId, int pageNum, int pageSize);
}
