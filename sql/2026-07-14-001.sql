-- F1：为 MCP Server 增加平台内置标识，并回填历史内置 MCP。
ALTER TABLE mcp_server
    ADD COLUMN IF NOT EXISTS is_builtin SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_mcp_server_is_builtin ON mcp_server (is_builtin);

COMMENT ON COLUMN mcp_server.is_builtin IS '是否平台内置：1=是，0=否';

UPDATE mcp_server
SET is_builtin = 1
WHERE (name = 'mcp-server-chart'
       AND install_type = 'npx'
       AND COALESCE(deploy_config ->> 'packageName', '') = '@antv/mcp-server-chart')
   OR (name = 'sequentialthinking'
       AND host = 'https://remote.mcpservers.org/sequentialthinking/mcp');
