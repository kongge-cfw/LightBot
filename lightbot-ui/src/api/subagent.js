import request from '../utils/request'

/**
 * SubAgent API
 */
export function getSubAgents(params) {
  return request.get('/subagents', { params })
}

export function getSubAgent(id) {
  return request.get(`/subagents/${id}`)
}

export function createSubAgent(data) {
  return request.post('/subagents', data)
}

export function updateSubAgent(data) {
  return request.put('/subagents', data)
}

export function deleteSubAgent(id) {
  return request.delete(`/subagents/${id}`)
}

export function getEnabledSubAgents() {
  return request.get('/subagents/enabled')
}

export function setSubAgentEnabled(id, enabled) {
  return request.put(`/subagents/${id}/enabled`, null, { params: { enabled } })
}

export function getSubAgentRuns(params) {
  return request.get('/subagents/runs', { params })
}

export function getSubAgentBatch(batchId, sessionId) {
  return request.get(`/subagents/batches/${batchId}`, { params: { sessionId } })
}

export function cancelSubAgentBatch(batchId, sessionId) {
  return request.post(`/subagents/batches/${batchId}/cancel`, null, { params: { sessionId } })
}

export function getSubAgentRun(taskId, sessionId) {
  return request.get(`/subagents/runs/${taskId}`, { params: { sessionId } })
}
