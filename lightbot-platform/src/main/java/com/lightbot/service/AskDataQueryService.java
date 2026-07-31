package com.lightbot.service;

import com.lightbot.dto.askdata.AskDataIntentIR;
import com.lightbot.dto.askdata.AskDatasetPreviewDTO;
import com.lightbot.vo.AskDataResultVO;
import com.lightbot.vo.AskDatasetVO;

import java.util.List;
import java.util.Set;

/**
 * 智能问数查询引擎
 *
 * @author finch
 * @since 2026-07-30
 */
public interface AskDataQueryService {

    /**
     * 在允许的数据集范围内执行 IR
     *
     * @param ir             意图
     * @param allowedDatasetIds null 表示不限制（控制台调试）；非 null 则必须命中白名单
     */
    AskDataResultVO execute(AskDataIntentIR ir, Set<Long> allowedDatasetIds);

    /**
     * 问数增强预览：用未落库的默认过滤 / 业务指标试跑
     *
     * @param datasetId 问数数据集 ID
     * @param dto       预览参数
     * @return 试跑结果
     */
    AskDataResultVO previewEnhancement(Long datasetId, AskDatasetPreviewDTO dto);

    /**
     * 目录检索（名称/编码/同义词）
     */
    List<AskDatasetVO> searchCatalog(String keyword, Set<Long> allowedDatasetIds);
}
