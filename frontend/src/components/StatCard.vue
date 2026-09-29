<script setup lang="ts">
defineProps<{ label: string; value: number | string; note: string; icon: string; tone?: 'blue' | 'teal' | 'violet' | 'amber' }>()
</script>

<template>
  <div class="stat-card" :class="`stat-${tone || 'blue'}`">
    <div class="stat-card-top"><span>{{ label }}</span><span class="stat-icon">{{ icon }}</span></div>
    <strong>{{ value }}</strong>
    <small>{{ note }}</small>
  </div>
</template>
