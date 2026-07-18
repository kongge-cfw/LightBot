package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.Message;
import com.lightbot.vo.ConversationSearchResultVO;

import java.util.List;

/**
 * 消息服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface MessageService extends IService<Message> {

    /**
     * 分页获取会话消息（按创建时间倒序，返回最新N条）
     *
     * @param sessionId 会话ID
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 分页消息列表
     */
    Page<Message> listBySessionIdPage(Long sessionId, int pageNum, int pageSize);

    /**
     * 获取会话的全部消息历史
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<Message> listBySessionId(Long sessionId);

    /**
     * 查询某次请求对应的助手消息。
     *
     * @param sessionId 会话 ID
     * @param requestId 请求 ID
     * @return 按创建时间正序排列的助手消息
     */
    List<Message> listAssistantByRequestId(Long sessionId, String requestId);

    /**
     * 查询本轮请求对应的用户输入消息。
     *
     * @param sessionId 会话ID
     * @param requestId 请求ID
     * @return 用户消息，不存在时返回 {@code null}
     */
    Message getUserByRequestId(Long sessionId, String requestId);

    /**
     * 查询指定消息前最近的一条用户消息，用于关联本轮输入附件。
     *
     * @param sessionId 会话 ID
     * @param beforeMessageId 助手消息 ID
     * @return 最近用户消息，不存在时返回 {@code null}
     */
    Message getPreviousUserMessage(Long sessionId, Long beforeMessageId);

    /**
     * 删除会话下的所有消息
     *
     * @param sessionId 会话ID
     */
    void deleteBySessionId(Long sessionId);

    /**
     * 删除单条消息（物理删除）
     *
     * @param messageId 消息ID
     * @param sessionId 会话ID（用于校验归属）
     */
    void deleteMessage(Long messageId, Long sessionId);

    /**
     * 搜索会话内的消息（内容模糊匹配）
     *
     * @param sessionId 会话ID
     * @param keyword   搜索关键词
     * @param pageNum   页码
     * @param pageSize  每页数量
     * @return 匹配的消息分页列表
     */
    Page<Message> searchBySessionId(Long sessionId, String keyword, int pageNum, int pageSize);

    /**
     * 跨会话搜索消息（按关键词匹配内容，限当前用户的会话）。
     *
     * @param userId  当前登录用户 ID
     * @param keyword 关键词（不可为空）
     * @param limit   最多返回条数
     * @return 每条命中消息 + 所属会话信息
     */
    List<ConversationSearchResultVO> searchConversations(Long userId, String keyword, int limit);

    /**
     * 按 message 内 toolEvents 索引读取完整 tool_result.result。
     * <p>历史列表场景已对超长 result 做预览截断，前端展开「查看结果」时调用此接口拉取完整内容</p>
     *
     * @param messageId  消息 ID
     * @param eventIndex toolEvents 数组下标
     * @return 完整 result 文本；事件不存在或非 tool_result 时返回 null
     */
    String getToolResultDetail(Long messageId, int eventIndex);

    /**
     * 切换消息收藏状态
     *
     * @param messageId 消息ID
     */
    void toggleStar(Long messageId);

    /**
     * 获取所有收藏消息（跨会话）
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 收藏消息分页列表
     */
    Page<Message> listStarred(int pageNum, int pageSize);
}
