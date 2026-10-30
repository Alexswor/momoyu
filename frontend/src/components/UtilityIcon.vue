<template>
  <svg class="utility-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path v-for="(path, index) in paths[name] || paths.arrow" :key="index" :d="path" />
  </svg>
</template>
<script setup>
defineProps({ name: { type: String, default: 'arrow' } });
const paths = {
  arrow: ['M4 12h15', 'm13 6 6 6-6 6'],
  down: ['m6 9 6 6 6-6'],
  film: ['M4 3h16v18H4Z', 'M8 3v18M16 3v18M4 8h4m8 0h4M4 16h4m8 0h4'],
  external: ['M14 4h6v6', 'm20 4-9 9', 'M10 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-4'],
};
</script>
<style scoped>
.utility-icon { display: inline-block; width: 1.1rem; height: 1.1rem; flex: 0 0 auto; vertical-align: middle; transition: rotate .2s ease; }
</style>
