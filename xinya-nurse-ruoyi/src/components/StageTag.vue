<template>
  <el-tag :color="color" :style="{ color: '#fff', border: 'none' }" effect="dark" size="small">
    {{ STAGE_LABELS[stage] || stage }}
  </el-tag>
</template>

<script setup lang="ts">
import type { ClinicalStage } from '@/types'
import { STAGE_LABELS, STAGE_COLORS } from '@/types'
import { computed } from 'vue'

const props = defineProps<{ stage: ClinicalStage }>()
const color = computed(() => STAGE_COLORS[props.stage] || '#78909C')
</script>
