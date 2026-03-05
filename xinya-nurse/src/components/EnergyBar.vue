<template>
  <div class="energy-bar">
    <el-progress
      :percentage="value"
      :stroke-width="strokeWidth"
      :color="barColor"
      :show-text="showText"
    />
    <span v-if="showLabel" class="energy-label">{{ value }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  strokeWidth?: number
  showText?: boolean
  showLabel?: boolean
}>(), {
  strokeWidth: 8,
  showText: false,
  showLabel: true
})

const barColor = computed(() => {
  if (props.value >= 60) return '#4CAF50'
  if (props.value >= 40) return '#FF9800'
  return '#F44336'
})
</script>

<style scoped lang="scss">
.energy-bar {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-progress { flex: 1; }

  .energy-label {
    font-size: 13px;
    font-weight: 600;
    min-width: 28px;
    text-align: right;
    color: var(--text-primary);
  }
}
</style>
