package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.dto.UserMemoryRequest;
import com.lightbot.dto.UserMemoryVO;
import com.lightbot.entity.UserMemory;
import com.lightbot.service.chat.ChatContext;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户长期记忆服务
 *
 * @author finch
 * @since 2026-07-09
 */
public interface UserMemoryService extends IService<UserMemory> {

    List<UserMemoryVO> listCurrentUserMemories(String keyword, String status);

    UserMemoryVO createCurrentUserMemory(UserMemoryRequest request);

    UserMemoryVO updateCurrentUserMemory(Long id, UserMemoryRequest request);

    void deleteCurrentUserMemory(Long id);

    UserMemoryVO updateCurrentUserMemoryStatus(Long id, String status);

    UserMemoryVO saveFromTool(Long userId, Long agentId, Long sessionId, Long sourceMessageId,
                              String memoryType, String content, List<String> keywords, BigDecimal confidence);

    List<UserMemory> searchForPrompt(Long userId, Long agentId, String query, int limit);

    String buildMemoryPrompt(Long userId, Long agentId, String query, int limit);

    void extractAsync(ChatContext ctx);
}
