<template>
  <span v-if="safeSvg" class="category-svg-icon" v-html="safeSvg" />
  <span v-else class="category-emoji-icon">{{ value || '🏷️' }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { sanitizeSvgText } from '../utils/sanitizeSvg.js'

const props = defineProps({ value: { type: String, default: '' } })
const safeSvg = computed(() => sanitizeSvgText(props.value))
</script>

<style scoped>
.category-svg-icon,.category-emoji-icon { display:inline-flex; align-items:center; justify-content:center; line-height:1; vertical-align:middle; }
.category-svg-icon :deep(svg) { display:block; width:1em; height:1em; max-width:1.25em; max-height:1.25em; }
.category-emoji-icon { font-size:1.15em; }
</style>
