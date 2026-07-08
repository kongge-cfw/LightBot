/** Debug Lab 消息 UI 态默认值 */
export const DEFAULT_DEBUG_UI_STATE = {
  streaming: false,
  reasoningExpanded: true,
  reasoningDone: true,
  toolsDone: true,
  toolExpanded: false,
  refsSectionExpanded: true,
}

/** @param {object} state */
export function normalizeDebugUiState(state = {}) {
  return {
    streaming: !!state.streaming,
    reasoningExpanded: state.reasoningExpanded !== false,
    reasoningDone: state.reasoningDone !== false,
    toolsDone: state.toolsDone !== false,
    toolExpanded: !!state.toolExpanded,
    refsSectionExpanded: state.refsSectionExpanded !== false,
  }
}
