import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useAlertStore = defineStore('alert', () => {
  const unresolvedCount = ref(0)

  async function fetchUnresolvedCount() {
    try {
      const data: any = await request.get('/alerts', {
        params: { resolved: false, pageSize: 1 }
      })
      unresolvedCount.value = data?.unresolvedCount ?? 0
    } catch {}
  }

  function decrement() {
    if (unresolvedCount.value > 0) unresolvedCount.value--
  }

  function reset() {
    unresolvedCount.value = 0
  }

  return { unresolvedCount, fetchUnresolvedCount, decrement, reset }
})
