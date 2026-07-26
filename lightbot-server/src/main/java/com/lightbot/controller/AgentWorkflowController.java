package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.WorkflowGraphDTO;
import com.lightbot.dto.DifyWorkflowExportPreviewVO;
import com.lightbot.dto.DifyWorkflowExportResult;
import com.lightbot.dto.DifyWorkflowImportPreviewVO;
import com.lightbot.dto.WorkflowNodeTestDTO;
import com.lightbot.dto.WorkflowAbandonDTO;
import com.lightbot.dto.WorkflowResumeDTO;
import com.lightbot.dto.WorkflowTestDTO;
import com.lightbot.vo.WorkflowTestResultVO;
import com.lightbot.vo.WorkflowTestRunDetailVO;
import com.lightbot.vo.WorkflowTestRunVO;
import com.lightbot.vo.WorkflowVersionVO;
import com.lightbot.service.WorkflowConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Agent 工作流配置接口
 */
@Tag(name = "Agent工作流", description = "工作流草稿、发布、版本与调试")
@RestController
@RequestMapping("/api/agents/{agentId}/workflow")
@RequiredArgsConstructor
public class AgentWorkflowController {

    private final WorkflowConfigService workflowConfigService;

    @Operation(summary = "获取工作流配置（草稿/发布状态）")
    @GetMapping
    public Result<Map<String, Object>> getConfig(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.getWorkflowConfig(agentId));
    }

    @Operation(summary = "暂存工作流草稿（跳过校验）")
    @PostMapping("/draft")
    public Result<Void> saveDraft(@PathVariable Long agentId, @RequestBody WorkflowGraphDTO graph) {
        workflowConfigService.saveDraft(agentId, graph);
        return Result.ok();
    }

    @Operation(summary = "预检Dify工作流导入")
    @PostMapping(value = "/import/dify/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DifyWorkflowImportPreviewVO> previewDifyImport(
            @PathVariable Long agentId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return Result.ok(workflowConfigService.previewDifyImport(agentId, readUtf8File(file)));
    }

    @Operation(summary = "导入Dify工作流到当前草稿")
    @PostMapping(value = "/import/dify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DifyWorkflowImportPreviewVO> importDifyWorkflow(
            @PathVariable Long agentId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return Result.ok(workflowConfigService.importDifyWorkflow(agentId, readUtf8File(file)));
    }

    @Operation(summary = "预检Dify工作流导出")
    @GetMapping("/export/dify/preview")
    public Result<DifyWorkflowExportPreviewVO> previewDifyExport(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.previewDifyExport(agentId));
    }

    @Operation(summary = "导出当前草稿为Dify YAML")
    @GetMapping(value = "/export/dify", produces = "application/x-yaml")
    public ResponseEntity<ByteArrayResource> exportDifyWorkflow(@PathVariable Long agentId) {
        DifyWorkflowExportResult result = workflowConfigService.exportDifyWorkflow(agentId);
        ByteArrayResource resource = new ByteArrayResource(result.getContent().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @Operation(summary = "发布工作流（需通过校验）")
    @PostMapping("/publish")
    public Result<Map<String, Object>> publish(@PathVariable Long agentId, @RequestBody WorkflowGraphDTO graph) {
        return Result.ok(workflowConfigService.publish(agentId, graph));
    }

    @Operation(summary = "校验工作流配置")
    @PostMapping("/validate")
    public Result<List<String>> validate(@PathVariable Long agentId, @RequestBody WorkflowGraphDTO graph) {
        return Result.ok(workflowConfigService.validate(agentId, graph));
    }

    @Operation(summary = "获取已发布工作流 IO Schema（子工作流参数映射）")
    @GetMapping("/io-schema")
    public Result<Map<String, Object>> getIoSchema(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.getIoSchema(agentId));
    }

    @Operation(summary = "工作流版本列表")
    @GetMapping("/versions")
    public Result<List<WorkflowVersionVO>> listVersions(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.listVersions(agentId));
    }

    @Operation(summary = "获取历史版本画布配置")
    @GetMapping("/versions/{version}")
    public Result<Map<String, Object>> getVersionGraph(
            @PathVariable Long agentId,
            @PathVariable Integer version) {
        return Result.ok(workflowConfigService.getVersionGraph(agentId, version));
    }

    @Operation(summary = "恢复历史版本到草稿")
    @PostMapping("/versions/{version}/restore")
    public Result<Void> restoreVersion(@PathVariable Long agentId, @PathVariable Integer version) {
        workflowConfigService.restoreVersion(agentId, version);
        return Result.ok();
    }

    @Operation(summary = "调试运行工作流")
    @PostMapping("/test")
    public Result<WorkflowTestResultVO> testRun(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowTestDTO request) {
        return Result.ok(workflowConfigService.testRun(agentId, request));
    }

    @Operation(summary = "调试运行工作流（SSE 实时推送）")
    @PostMapping(value = "/test/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter testRunStream(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowTestDTO request) {
        return workflowConfigService.testRunStream(agentId, request);
    }

    @Operation(summary = "人工确认后恢复工作流")
    @PostMapping("/resume")
    public Result<WorkflowTestResultVO> resume(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowResumeDTO request) {
        return Result.ok(workflowConfigService.resumeWorkflow(agentId, request));
    }

    @Operation(summary = "人工确认后恢复工作流（SSE 实时推送）")
    @PostMapping(value = "/resume/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeStream(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowResumeDTO request) {
        return workflowConfigService.resumeWorkflowStream(agentId, request);
    }

    @Operation(summary = "放弃人工确认（终止挂起的工作流）")
    @PostMapping("/abandon")
    public Result<Void> abandonConfirm(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowAbandonDTO request) {
        workflowConfigService.abandonWorkflowConfirm(agentId, request);
        return Result.ok();
    }

    @Operation(summary = "单节点调试运行")
    @PostMapping("/test-node")
    public Result<WorkflowTestResultVO> testNode(
            @PathVariable Long agentId,
            @RequestBody @Valid WorkflowNodeTestDTO request) {
        return Result.ok(workflowConfigService.testNode(agentId, request));
    }

    @Operation(summary = "测试运行历史列表")
    @GetMapping("/test-runs")
    public Result<List<WorkflowTestRunVO>> listTestRuns(@PathVariable Long agentId) {
        return Result.ok(workflowConfigService.listTestRuns(agentId));
    }

    @Operation(summary = "测试运行详情")
    @GetMapping("/test-runs/{runId}")
    public Result<WorkflowTestRunDetailVO> getTestRun(
            @PathVariable Long agentId,
            @PathVariable String runId) {
        return Result.ok(workflowConfigService.getTestRun(agentId, runId));
    }

    @Operation(summary = "删除单条测试记录")
    @DeleteMapping("/test-runs/{runId}")
    public Result<Void> deleteTestRun(
            @PathVariable Long agentId,
            @PathVariable String runId) {
        workflowConfigService.deleteTestRun(agentId, runId);
        return Result.ok();
    }

    @Operation(summary = "清空测试历史")
    @DeleteMapping("/test-runs")
    public Result<Void> clearTestRuns(@PathVariable Long agentId) {
        workflowConfigService.clearTestRuns(agentId);
        return Result.ok();
    }

    private String readUtf8File(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 Dify YAML 文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".yml") && !fileName.endsWith(".yaml"))) {
            throw new IllegalArgumentException("仅支持 .yml 或 .yaml 文件");
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
