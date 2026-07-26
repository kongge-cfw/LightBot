package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.dto.datacenter.DataPoolBatchCreateDTO;
import com.lightbot.dto.datacenter.DataPoolBatchDeleteDTO;
import com.lightbot.dto.datacenter.DataPoolRecordDTO;
import com.lightbot.service.DataPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 数据池开放 API（供外部系统通过登录会话或 API Key 调用）
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "开放API-数据池")
@RestController
@RequestMapping("/api/open/v1/data-pools/{modelId}/records")
@RequiredArgsConstructor
public class OpenDataPoolController {

    private final DataPoolService dataPoolService;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "查询列表（分页）")
    public Result<Page<Map<String, Object>>> page(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String filters) {
        return Result.ok(dataPoolService.page(modelId, pageNum, pageSize, keyword, parseFilters(filters)));
    }

    @PostMapping
    @Operation(summary = "新增")
    public Result<Map<String, Object>> create(@PathVariable Long modelId,
                                              @Valid @RequestBody DataPoolRecordDTO dto) {
        return Result.ok(dataPoolService.create(modelId, dto.getData()));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量新增")
    public Result<List<Map<String, Object>>> batchCreate(@PathVariable Long modelId,
                                                         @Valid @RequestBody DataPoolBatchCreateDTO dto) {
        return Result.ok(dataPoolService.batchCreate(modelId, dto.getRecords()));
    }

    @PutMapping("/{recordId}")
    @Operation(summary = "修改")
    public Result<Map<String, Object>> update(@PathVariable Long modelId,
                                              @PathVariable Long recordId,
                                              @Valid @RequestBody DataPoolRecordDTO dto) {
        return Result.ok(dataPoolService.update(modelId, recordId, dto.getData()));
    }

    @DeleteMapping("/{recordId}")
    @Operation(summary = "删除")
    public Result<Void> delete(@PathVariable Long modelId, @PathVariable Long recordId) {
        dataPoolService.delete(modelId, recordId);
        return Result.ok();
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除")
    public Result<Integer> batchDelete(@PathVariable Long modelId,
                                       @Valid @RequestBody DataPoolBatchDeleteDTO dto) {
        return Result.ok(dataPoolService.batchDelete(modelId, dto.getIds()));
    }

    private Map<String, Object> parseFilters(String filters) {
        if (!StringUtils.hasText(filters)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(filters, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
