package com.lightbot.subagent.spi;

import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.SubAgentTaskBatch;

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
}
