# 业务页接入说明（H5 HTML 主路径）

## 1. 在能力中心注册

填写 `pageType`，并在 **H5 页面** 文本框中直接编写完整 HTML（含 CSS/JS）。  
对话内通过 iframe `srcdoc` 嵌套渲染，**不需要外链**。

## 2. postMessage 协议

父页面 → H5：

```js
{ source: 'lightbot-business-page', type: 'init', payload: { pageType, title, props, options, actions } }
```

H5 → 父页面：

```js
parent.postMessage({ source: 'lightbot-business-page', type: 'ready' }, '*')
parent.postMessage({ source: 'lightbot-business-page', type: 'resize', height: 520 }, '*')
parent.postMessage({ source: 'lightbot-business-page', type: 'submit', values: { /* 结果 */ } }, '*')
parent.postMessage({ source: 'lightbot-business-page', type: 'cancel' }, '*')
```

新建页默认带有可运行的示例 HTML，可直接改字段与请求逻辑。

## 3. 优先级

宿主 `registerBusinessPageComponent` > **pageHtml（srcdoc）** > pageUrl 外链 > formSchema 兜底。
