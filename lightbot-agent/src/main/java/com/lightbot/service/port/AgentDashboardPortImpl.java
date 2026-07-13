package com.lightbot.service.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.Agent;
import com.lightbot.enums.AgentStatus;
import com.lightbot.mapper.AgentMapper;
import com.lightbot.service.port.AgentDashboardPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 域 Dashboard 统计实现。
 */
@Component
@RequiredArgsConstructor
public class AgentDashboardPortImpl implements AgentDashboardPort {

    private final AgentMapper agentMapper;

    @Override
    public long countAgents() {
        return agentMapper.selectCount(null);
    }

    @Override
    public Map<String, Object> getAgentStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", agentMapper.selectCount(null));

        // 1. 按状态分组计数（带中文 label）
        List<Map<String, Object>> statusList = new ArrayList<>();
        for (AgentStatus status : AgentStatus.values()) {
            Long count = agentMapper.selectCount(
                    new LambdaQueryWrapper<Agent>().eq(Agent::getStatus, status));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", status.getCode());
            item.put("label", status.getDesc());
            item.put("count", count);
            statusList.add(item);
        }
        stats.put("statusList", statusList);

        // 2. 保留旧字段兼容
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusList.forEach(item -> statusCounts.put(
                (String) item.get("code"), ((Number) item.get("count")).longValue()));
        stats.put("statusCounts", statusCounts);

        // 3. 最近 5 个 Agent
        List<Agent> recent = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>()
                        .orderByDesc(Agent::getCreateTime)
                        .last("LIMIT 5"));
        List<Map<String, Object>> recentList = recent.stream().map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId().toString());
            item.put("name", a.getName());
            AgentStatus status = a.getStatus();
            item.put("status", status != null ? status.getCode() : "draft");
            item.put("statusLabel", status != null ? status.getDesc() : "草稿");
            item.put("createTime", a.getCreateTime());
            return item;
        }).collect(Collectors.toList());
        stats.put("recent", recentList);
        return stats;
    }
}
