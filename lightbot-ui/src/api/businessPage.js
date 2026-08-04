import request from '../utils/request'

/** 管理端：全部业务页 */
export function listBusinessPages() {
  return request.get('/business-pages')
}

/** 建设者：已启用业务页 */
export function listEnabledBusinessPages() {
  return request.get('/business-pages/enabled')
}

export function upsertBusinessPage(data) {
  return request.post('/business-pages', data)
}

export function setBusinessPageEnabled(id, enabled) {
  return request.put(`/business-pages/${id}/enabled`, null, { params: { enabled } })
}

export function deleteBusinessPage(id) {
  return request.delete(`/business-pages/${id}`)
}

export function getApiKeyBusinessPageConfig(apiKeyId) {
  return request.get(`/business-pages/api-keys/${apiKeyId}/config`)
}

export function updateApiKeyBusinessPageConfig(apiKeyId, data) {
  return request.put(`/business-pages/api-keys/${apiKeyId}/config`, data)
}
