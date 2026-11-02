<template>
  <svg class="utility-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path v-for="(path, index) in paths[name] || paths.arrow" :key="index" :d="path" />
  </svg>
</template>
<script setup>
defineProps({ name: { type: String, default: 'arrow' } });
const paths = {
  layers: ['M3 7h12v12H3Z', 'M9 3h12v12H9Z'],
  settings: ['M4 7h16M4 17h16', 'M8 4v6M16 14v6'],
  pin: ['m9 3 6 0-1 6 4 4H6l4-4Z', 'M12 13v8'],
  cursor: ['m5 3 14 9-7 1-3 7Z'],
  popup: ['M4 4h16v16H4Z', 'M11 11h9v9h-9Z', 'M7 7h3'],
  list: ['M9 6h12M9 12h12M9 18h12M3 6h1M3 12h1M3 18h1'],
  pip: ['M4 4h16v16H4Z', 'M12 12h6v6h-6Z'],
  back: ['M20 12H4', 'm10 6-6 6 6 6'],
  eye: ['M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0'],
  blur: ['M9 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0M19 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0M9 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0M19 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0'],
  arrow: ['M4 12h15', 'm13 6 6 6-6 6'],
  down: ['m6 9 6 6 6-6'],
  film: ['M4 3h16v18H4Z', 'M8 3v18M16 3v18M4 8h4m8 0h4M4 16h4m8 0h4'],
  external: ['M14 4h6v6', 'm20 4-9 9', 'M10 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-4'],
};
</script>
<style scoped>
.utility-icon { display: inline-block; width: 1.1rem; height: 1.1rem; flex: 0 0 auto; vertical-align: middle; transition: rotate .2s ease; }
</style>
