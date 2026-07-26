package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.entity.AutomationJob;
import com.lightbot.vo.AutomationJobRunVO;
import com.lightbot.vo.AutomationJobVO;

import java.util.List;

/**
 * 自动化定时任务服务
 *
 * @author finch
 * @since 2026-07-26
 */
public interface AutomationJobService extends IService<AutomationJob> {

    List<AutomationJobVO> listMine(String keyword, Boolean enabled);

    AutomationJobVO getMine(Long id);

    AutomationJobVO create(AutomationJobSaveDTO dto);

    AutomationJobVO update(Long id, AutomationJobSaveDTO dto);

    void delete(Long id);

    AutomationJobVO setEnabled(Long id, boolean enabled);

    /**
     * 立即执行：创建 running 记录并返回 runId（不推进 cron next_run_at）
     *
     * @param id 任务 ID
     * @return runId，由调用方在事务外执行 {@link #executeClaimedRun(Long)}
     */
    Long prepareManualRun(Long id);

    Page<AutomationJobRunVO> pageRuns(String keyword, String status, int pageNum, int pageSize);

    AutomationJobRunVO getRunMine(Long runId);

    /**
     * 扫描并抢占到期任务，返回待执行的 runId 列表
     *
     * @param limit 单次上限
     * @return runId 列表
     */
    List<Long> claimDueJobs(int limit);

    /** 执行已抢占的 run（异步线程调用） */
    void executeClaimedRun(Long runId);

    /** 回收超时 running */
    int reclaimExpiredRuns(int limit);
}
