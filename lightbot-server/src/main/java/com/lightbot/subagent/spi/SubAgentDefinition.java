package com.lightbot.subagent.spi;

import com.lightbot.entity.SubAgent;

/**
 * SubAgent 的运行时定义。
 *
 * @param source 已通过当前 Agent 绑定范围校验的配置实体
 */
public record SubAgentDefinition(SubAgent source) {

    public String name() {
        return source.getName();
    }

    public String displayName() {
        return source.getDisplayName() != null && !source.getDisplayName().isBlank()
                ? source.getDisplayName() : source.getName();
    }
}
