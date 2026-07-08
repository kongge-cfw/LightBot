import {
  TOOL_RENDERERS,
  TOOL_DISPLAY_NAMES,
  HIDDEN_TOOL_NAMES,
  hasToolRenderer,
} from '@/components/toolRegistry'
import {
  CAPABILITY_BLOCK_RENDERERS,
} from '@/components/capabilities/capabilityRegistry'
import { SUBAGENT_CALL_EVENT_TYPE } from '@/components/capabilities/subagentRegistry'
import {
  SKILL_ITEM_RENDERERS,
  SKILL_META,
  BUILTIN_SKILL_SLUGS,
  SKILL_ACTIVE_EVENT_TYPE,
} from '@/components/skills/skillRegistry'

/** 工具注册表只读数据 */
export function getToolRegistryRows() {
  const names = new Set([
    ...Object.keys(TOOL_RENDERERS),
    ...Object.keys(TOOL_DISPLAY_NAMES),
    ...HIDDEN_TOOL_NAMES,
  ])
  return Array.from(names).sort().map((toolName) => ({
    toolName,
    displayName: TOOL_DISPLAY_NAMES[toolName] || toolName,
    hasRenderer: hasToolRenderer(toolName),
    hidden: HIDDEN_TOOL_NAMES.has(toolName),
    component: TOOL_RENDERERS[toolName] ? '专用 *Result.vue' : (HIDDEN_TOOL_NAMES.has(toolName) ? 'CapabilityPanel' : 'BaseToolCall'),
  }))
}

/** 能力块注册表只读数据 */
export function getCapabilityRegistryRows() {
  return [
    {
      eventType: SKILL_ACTIVE_EVENT_TYPE,
      component: 'SkillActiveBlock',
      registered: !!CAPABILITY_BLOCK_RENDERERS[SKILL_ACTIVE_EVENT_TYPE],
    },
    {
      eventType: SUBAGENT_CALL_EVENT_TYPE,
      component: 'SubAgentCallBlock',
      registered: !!CAPABILITY_BLOCK_RENDERERS[SUBAGENT_CALL_EVENT_TYPE],
    },
  ]
}

/** Skill 列表项注册表只读数据 */
export function getSkillRegistryRows() {
  const slugs = new Set([
    ...BUILTIN_SKILL_SLUGS,
    ...Object.keys(SKILL_META),
    ...Object.keys(SKILL_ITEM_RENDERERS),
  ])
  return Array.from(slugs).sort().map((slug) => ({
    slug,
    displayName: SKILL_META[slug]?.displayName || slug,
    hasCustomRenderer: Object.prototype.hasOwnProperty.call(SKILL_ITEM_RENDERERS, slug),
    component: SKILL_ITEM_RENDERERS[slug] ? '定制 SkillItem' : 'DefaultSkillItem',
    builtin: BUILTIN_SKILL_SLUGS.includes(slug),
  }))
}
