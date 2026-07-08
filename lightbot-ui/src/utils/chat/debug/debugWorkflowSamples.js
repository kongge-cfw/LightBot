import { getPresetById } from './debugPresets'

/** 工作流专项样例 */
export const WORKFLOW_DEBUG_SAMPLES = [
  { id: 'workflow-steps', label: '工作流步骤', presetId: 'workflow-steps' },
  { id: 'workflow-confirm', label: '工作流待确认', presetId: 'workflow-confirm' },
  { id: 'tool-error', label: '工具错误（对照）', presetId: 'tool-error' },
]

export function getWorkflowSampleMessage(sampleId) {
  const sample = WORKFLOW_DEBUG_SAMPLES.find((s) => s.id === sampleId)
  if (!sample) return null
  return getPresetById(sample.presetId)
}

export function getWorkflowSampleOptions() {
  return WORKFLOW_DEBUG_SAMPLES.map((s) => ({ value: s.id, label: s.label }))
}

/** 默认 workflowEvents 编辑模板 */
export function createDefaultWorkflowEventsJson() {
  return JSON.stringify([
    { type: 'workflow_node_start', nodeId: 'n1', nodeLabel: '开始', nodeType: 'start' },
    { type: 'workflow_node_complete', nodeId: 'n1', nodeLabel: '开始', success: true, durationMs: 10 },
    { type: 'workflow_complete', success: true },
  ], null, 2)
}
