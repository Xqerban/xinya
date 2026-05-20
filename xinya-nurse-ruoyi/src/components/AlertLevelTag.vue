<template>
  <el-tag :type="tagType" effect="light" size="small">
    {{ LABELS[level] }}
  </el-tag>
</template>

<script setup lang="ts">
import type { AlertLevel } from '@/types'
import { computed } from 'vue'

const props = defineProps<{ level: AlertLevel }>()

const LABELS: Record<AlertLevel, string> = {
  info: '提示',
  warning: '警告',
  critical: '严重'
}

const tagType = computed(() => {
  if (props.level === 'critical') return 'danger'
  if (props.level === 'warning') return 'warning'
  return 'info'
})
</script>
