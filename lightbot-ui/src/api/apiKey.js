import request from '../utils/request'

/** 查询企业 API Key 列表（管理员） */
export function listApiKeys() {
  return request.get('/api-keys')
}

/** 创建企业 API Key（管理员） */
export function createApiKey(data) {
  return request.post('/api-keys', data)
}

/** 启用/禁用企业 API Key（管理员） */
export function toggleApiKey(id) {
  return request.patch(`/api-keys/${id}/toggle`)
}

/** 删除企业 API Key（管理员） */
export function deleteApiKey(id) {
  return request.delete(`/api-keys/${id}`)
}

/** 重新生成企业 API Key（管理员） */
export function regenerateApiKey(id) {
  return request.post(`/api-keys/${id}/regenerate`)
}
