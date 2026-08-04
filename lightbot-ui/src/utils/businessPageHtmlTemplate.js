/** 新建业务页时的默认 H5 模板（iframe srcdoc） */
export const DEFAULT_BUSINESS_PAGE_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>业务办理</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; font-family: system-ui, sans-serif; padding: 12px; color: #111; }
    label { display: block; font-size: 13px; margin: 10px 0 4px; }
    input, select, textarea { width: 100%; padding: 8px 10px; border: 1px solid #d4d4d8; border-radius: 8px; font-size: 14px; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
    button { padding: 8px 14px; border-radius: 8px; border: 1px solid #d4d4d8; background: #fff; cursor: pointer; }
    button.primary { background: #171717; color: #fff; border-color: #171717; }
    .hint { font-size: 12px; color: #71717a; margin: 0 0 8px; }
  </style>
</head>
<body>
  <p class="hint" id="hint">请填写信息后提交</p>
  <label>户号 <input id="accountNo" /></label>
  <label>金额（元） <input id="amount" type="number" min="1" /></label>
  <div class="actions">
    <button type="button" id="btnCancel">取消</button>
    <button type="button" class="primary" id="btnSubmit">提交</button>
  </div>
  <script>
    const SOURCE = 'lightbot-business-page';
    let ctx = { props: {}, options: {}, pageType: '' };
    function applyInit(payload) {
      ctx = payload || ctx;
      if (ctx.options && ctx.options.hint) document.getElementById('hint').textContent = ctx.options.hint;
      const p = ctx.props || {};
      if (p.accountNo != null) document.getElementById('accountNo').value = p.accountNo;
      if (p.amount != null) document.getElementById('amount').value = p.amount;
      parent.postMessage({ source: SOURCE, type: 'resize', height: document.body.scrollHeight + 24 }, '*');
    }
    window.addEventListener('message', (e) => {
      if (e.data && e.data.source === SOURCE && e.data.type === 'init') applyInit(e.data.payload);
    });
    parent.postMessage({ source: SOURCE, type: 'ready' }, '*');
    document.getElementById('btnSubmit').onclick = () => {
      parent.postMessage({
        source: SOURCE,
        type: 'submit',
        values: {
          accountNo: document.getElementById('accountNo').value,
          amount: Number(document.getElementById('amount').value || 0)
        }
      }, '*');
    };
    document.getElementById('btnCancel').onclick = () => {
      parent.postMessage({ source: SOURCE, type: 'cancel' }, '*');
    };
  </script>
</body>
</html>
`
