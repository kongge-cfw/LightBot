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

export function getToolBlockOffsets(msg) {
  if (msg._toolBlockOffsets?.length > 0) return msg._toolBlockOffsets
  const fromEvents = [...new Set(
    (msg._toolEvents || [])
      .filter(e => e.type === 'tool_call' || e.type === 'subagent_call')
      .map(e => e.contentOffset)
      .filter(o => o != null && o >= 0)
  )]
  return fromEvents.sort((a, b) => a - b)
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

export function splitContentByOffsets(msg) {
  const content = msg.content || ''
  const offsets = getToolBlockOffsets(msg)
  if (offsets.length === 0) {
    if ((msg._toolEvents || []).length > 0) {
      return [
        { type: 'tool', offset: -1 },
        ...(content ? [{ type: 'text', text: content }] : []),
      ]
    }
    return [{ type: 'text', text: content }]
  }

  const segments = []
  let lastIdx = 0
  for (const offset of offsets) {
    if (offset > lastIdx && offset <= content.length) {
      segments.push({ type: 'text', text: content.substring(lastIdx, offset) })
    }
    segments.push({ type: 'tool', offset })
    lastIdx = Math.max(lastIdx, offset)
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
