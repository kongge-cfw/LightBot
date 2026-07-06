<template>
  <div v-if="blockEvents.length > 0" class="capability-panel">
    <component
      v-for="(entry, i) in blockEvents"
      :key="entry.key"
      :is="entry.renderer"
      :event="entry.event"
      :events="events"
      :all-events="allEvents || events"
      :event-index="entry.index"
      :is-done="isDone"
      :default-expanded="defaultExpanded"
      @heightChange="onHeightChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getCapabilityBlockRenderer } from './capabilityRegistry.js'

const props = defineProps({
  events: { type: Array, default: () => [] },
  allEvents: { type: Array, default: null },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true },
})

const emit = defineEmits(['heightChange'])

const blockEvents = computed(() =>
  (props.events || [])
    .map((event, index) => {
      const renderer = getCapabilityBlockRenderer(event?.type)
      if (!renderer) return null
      return {
        event,
        index,
        renderer,
        key: `${event.type}-${event.contentOffset ?? 'top'}-${index}`,
      }
    })
    .filter(Boolean)
)

function onHeightChange(evt) {
  emit('heightChange', evt)
}
</script>

<style scoped>
.capability-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
</style>
