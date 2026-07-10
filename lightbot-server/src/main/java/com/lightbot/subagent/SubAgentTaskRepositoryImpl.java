package com.lightbot.subagent;

import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.SubAgentTaskBatch;
import com.lightbot.mapper.SubAgentRunMapper;
import com.lightbot.mapper.SubAgentTaskBatchMapper;
import com.lightbot.subagent.spi.SubAgentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** SubAgent 任务数据访问实现。 */
@Repository
@RequiredArgsConstructor
public class SubAgentTaskRepositoryImpl implements SubAgentTaskRepository {

    private final SubAgentRunMapper subAgentRunMapper;
    private final SubAgentTaskBatchMapper subAgentTaskBatchMapper;

    @Override
    public SubAgentTaskBatch findBatch(String batchId) {
        return subAgentTaskBatchMapper.selectByBatchId(batchId);
    }

    @Override
    public void saveBatch(SubAgentTaskBatch batch) {
        if (batch.getId() == null) {
            subAgentTaskBatchMapper.insert(batch);
        } else {
            subAgentTaskBatchMapper.updateById(batch);
        }
    }

    @Override
    public SubAgentRun findTask(String taskId) {
        return subAgentRunMapper.selectByRequestId(taskId);
    }

    @Override
    public List<SubAgentRun> findTasks(String batchId) {
        return subAgentRunMapper.selectByBatchId(batchId);
    }

    @Override
    public void saveTask(SubAgentRun task) {
        if (task.getId() == null) {
            subAgentRunMapper.insert(task);
        } else {
            subAgentRunMapper.updateById(task);
        }
    }

    @Override
    public int requestCancelTask(String taskId) {
        return subAgentRunMapper.requestCancelByRequestId(taskId);
    }

    @Override
    public int requestCancelBatch(String batchId) {
        subAgentTaskBatchMapper.requestCancelByBatchId(batchId);
        return subAgentRunMapper.requestCancelByBatchId(batchId);
    }
}
