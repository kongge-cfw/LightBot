# 业务页接入说明（H5）

本目录仅放接入文档，**不预装**任何业务 Vue 卡片（业务 UI 以能力中心登记的 `pageHtml` / `pageUrl` 为准）。

## 1. 在能力中心注册

二选一：

- **内嵌 HTML**：编写完整 HTML（含 CSS/JS）
- **外链网页**：填写已部署地址

## 2. 内嵌 HTML：页面可零感知

宿主会在 `srcdoc` 中**静默注入桥接**：

1. 拦截成功的 `fetch` / `XHR`（默认 `POST` / `PUT` / `PATCH`）
2. 把请求体（表单字段）作为结果回传对话；接口响开放 `extra`
3. 从页面 `<label>`（或 `data-label`）采集字段展示名，**平台不做字段中英词典**
4. 识别「取消」按钮（文案为取消，或 id/class 含 `cancel`，或 `data-lightbot-cancel`）

业务页只需写自己的校验与接口，**不必**写 `postMessage`。

```js
const res = await fetch(API_URL || '/__lightbot_bp_demo__', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(form),
})
// 成功后无需通知父页面；宿主已自动回传
```

### 字段标签（办结摘要）

请保证每个输入有可见 `<label>`，并与控件关联（包裹控件 / `for=id` / 紧邻上方）。摘要展示的是 label 原文，不会把 `name` 翻译成中文。

### 收窄自动捕获（可选）

通过工具 `options` 传入：

```json
{
  "autoCapture": true,
  "captureMethods": ["POST"],
  "captureUrlIncludes": ["/api/your/path"],
  "captureUrlExcludes": ["/api/log"]
}
```

### 兼容显式 API（可选）

```js
LightBot.submit({ orderId: 'x' })
LightBot.cancel()
```

## 3. 外链网页

跨域无法注入桥接。可选：

- 页面内 `postMessage` 回传
- 业务后端回调 LightBot

## 4. 优先级

宿主 `registerBusinessPageComponent` > **pageHtml（srcdoc + 静默桥接）** > pageUrl 外链。
