/**
 * Chat 能力类事件分区（Skill / SubAgent 与工具事件分离）
 */
import { SKILL_ACTIVE_EVENT_TYPE } from '../../components/skills/skillRegistry.js'
import { SUBAGENT_EVENT_TYPES } from '../../components/capabilities/subagentRegistry.js'

export const CAPABILITY_EVENT_TYPES = new Set([
  SKILL_ACTIVE_EVENT_TYPE,
  ...SUBAGENT_EVENT_TYPES,
])

export function isCapabilityEvent(event) {
  return CAPABILITY_EVENT_TYPES.has(event?.type)
}

export function isSkillActiveEvent(event) {
  return event?.type === SKILL_ACTIVE_EVENT_TYPE
}

export function isSubagentCapabilityEvent(event) {
  return SUBAGENT_EVENT_TYPES.has(event?.type)
}

export function getCapabilityEvents(msg) {
  return (msg?._toolEvents || []).filter(e => CAPABILITY_EVENT_TYPES.has(e.type))
}

/** 消息顶部展示的 Skill 启用块 */
export function getTopSkillEvents(msg) {
  return getCapabilityEvents(msg).filter(isSkillActiveEvent)
}

/** @deprecated 使用 getTopSkillEvents */
export function getTopCapabilityEvents(msg) {
  return getTopSkillEvents(msg)
}

/** 工具块 inline 区域的 SubAgent 能力事件（不含 skill_active） */
export function getCapabilityEventsForOffset(msg, offset) {
  if (offset === -1) {
    return getCapabilityEvents(msg).filter(e => !isSkillActiveEvent(e))
  }
  return getCapabilityEvents(msg).filter(
    e => !isSkillActiveEvent(e) && e.contentOffset == offset
  )
}

export function getInlineCapabilityEvents(msg) {
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length > 0) return []
  return getCapabilityEvents(msg).filter(e => !isSkillActiveEvent(e))
}

export function getPureToolEvents(events) {
  return (events || []).filter(e => !CAPABILITY_EVENT_TYPES.has(e.type))
}

export function getSubagentCallEvents(msg) {
  return (msg?._toolEvents || []).filter(e => e.type === 'subagent_call')
}

export function hasSubagentCalls(msg) {
  return getSubagentCallEvents(msg).length > 0
}

export function getToolBlockOffsets(msg) {
  const fromEvents = [...new Set(
    (msg._toolEvents || [])
      .filter(e => e.type === 'tool_call' || e.type === 'subagent_call')
      .map(e => e.contentOffset)
      .filter(o => o != null && o >= 0)
      .map(o => Number(o))
  )].sort((a, b) => a - b)
  // 以 toolEvents 为准；metadata.toolBlockOffsets 可能滞后于 realign 后的 contentOffset
  if (fromEvents.length > 0) return fromEvents
  if (msg._toolBlockOffsets?.length > 0) return msg._toolBlockOffsets.map(o => Number(o))
  return []
}

export function getToolEventsForOffset(msg, offset) {
  const events = msg._toolEvents || []
  if (offset === -1) {
    return events.filter(e => e.contentOffset == null)
  }
  const matched = events.filter(e => e.contentOffset == offset)
  if (matched.length > 0) return matched
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length === 1 && offsets[0] == offset) {
    return events.filter(e => e.contentOffset == null)
  }
  return matched
}

export function isToolBlockDone(msg, offset) {
  if (offset === -1) {
    if (!msg._streaming) return true
    return (msg._toolEvents || []).some(e =>
      e.type === 'tool_result' || e.type === 'subagent_result' || e.type === 'subagent_error')
  }
  if (msg._toolBlocksDone?.some(o => o == offset)) return true
  if (!msg._streaming) return true
  const atOffset = getToolEventsForOffset(msg, offset)
  return atOffset.some(e =>
    e.type === 'tool_result' || e.type === 'subagent_result' || e.type === 'subagent_error')
}

export function markToolBlockDone(msg, offset) {
  if (offset == null || offset < 0) return
  if (!msg._toolBlocksDone) msg._toolBlocksDone = []
  if (!msg._toolBlocksDone.some(o => o == offset)) {
    msg._toolBlocksDone.push(offset)
  }
}

/**
 * 根据 contentPrefixAnchor 解析 SubAgent 在正文中的真实切分点。
 * segment.offset 仍用 metadata 中的 contentOffset（用于事件关联），
 * 切分位置用 splitAt（避免 offset 漂移导致「好<组件>的」截断首字）。
 */
export function resolveToolBlockSplitAt(content, callEvent, fallbackOffset) {
  const anchor = callEvent?.contentPrefixAnchor || callEvent?.contentPrefixSnapshot
  if (typeof anchor === 'string' && anchor.length > 0) {
    if (content.startsWith(anchor)) return anchor.length
    const idx = content.indexOf(anchor)
    if (idx >= 0) return idx + anchor.length
  }
  const fb = Number(fallbackOffset)
  if (!Number.isFinite(fb) || fb < 0) return 0
  return Math.min(fb, content.length)
}

function buildSubagentCallByOffset(msg) {
  const map = new Map()
  for (const call of getSubagentCallEvents(msg)) {
    if (call.contentOffset != null) {
      map.set(Number(call.contentOffset), call)
    }
  }
  return map
}

/** 按 block offset 查找 SubAgent call（兼容 metadata offset 与 event offset 不一致） */
function findSubagentCallForBlock(msg, blockOffset, subagentByOffset) {
  const exact = subagentByOffset.get(Number(blockOffset))
  if (exact) return exact
  const calls = getSubagentCallEvents(msg)
  if (calls.length === 1) return calls[0]
  return calls.find(c => Number(c.contentOffset) === Number(blockOffset)) || null
}

export function splitContentByOffsets(msg) {
  const content = msg.content || ''
  const rawOffsets = getToolBlockOffsets(msg)
  const subagentByOffset = buildSubagentCallByOffset(msg)
  const pureToolEvents = getPureToolEvents(msg._toolEvents)

  if (rawOffsets.length === 0) {
    if ((msg._toolEvents || []).length > 0 && pureToolEvents.length > 0) {
      return [
        { type: 'tool', offset: -1 },
        ...(content ? [{ type: 'text', text: content }] : []),
      ]
    }
    return content ? [{ type: 'text', text: content }] : []
  }

  const segments = []
  let lastIdx = 0

  for (const rawOffset of rawOffsets) {
    const call = findSubagentCallForBlock(msg, rawOffset, subagentByOffset)
    const splitAt = call
      ? resolveToolBlockSplitAt(content, call, call.contentOffset ?? rawOffset)
      : Math.min(Math.max(0, Number(rawOffset)), content.length)

    if (splitAt > lastIdx) {
      segments.push({ type: 'text', text: content.substring(lastIdx, splitAt) })
    }
    // 事件关联用 call 上的 contentOffset（与 _toolEvents 一致），避免 stale toolBlockOffsets 对不上
    const eventOffset = call?.contentOffset != null ? Number(call.contentOffset) : Number(rawOffset)
    segments.push({ type: 'tool', offset: eventOffset })
    lastIdx = splitAt
  }

  if (lastIdx < content.length) {
    segments.push({ type: 'text', text: content.substring(lastIdx) })
  }

  return segments
}

export function isSegmentFinalized(msg, segment, index) {
  if (!msg?._streaming) return true
  if (segment.type !== 'text') return true
  const segments = splitContentByOffsets(msg)
  const lastTextIndex = [...segments].map((s, i) => ({ s, i })).reverse().find(item => item.s.type === 'text')?.i
  return index !== lastTextIndex
}
