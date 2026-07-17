import request from '../utils/request'

export function getTaskList(params) {
  return request.get('/tasks', { params })
}

export function getRunningTaskCount() {
  return request.get('/tasks/running-count')
}

export function getTaskTypeCounts() {
  return request.get('/tasks/type-counts')
}

export function getTask(taskId) {
  return request.get(`/tasks/${taskId}`)
}

/**
 * 获取任务实时进度快照（Redis Hash 直读，毫秒级可达）
 * 用于详情抽屉轮询，避免 5s 列表轮询的进度延迟
 */
export function getTaskProgress(taskId) {
  return request.get(`/tasks/${taskId}/progress`)
}

export function cancelTask(taskId) {
  return request.post(`/tasks/${taskId}/cancel`)
}

export function deleteTask(taskId) {
  return request.delete(`/tasks/${taskId}`)
}
