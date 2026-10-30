<template>
  <span class="whale-menu-icon" :data-action="action" aria-hidden="true">
    <img :src="`/whale/ui/menu-${action}.webp`" alt="" decoding="async" draggable="false" />
  </span>
</template>
<script setup>
defineProps({
  action: { type: String, required: true, validator: (value) => ['pick', 'top', 'hide'].includes(value) },
});
</script>
<style scoped>
.whale-menu-icon { display: inline-flex; flex: 0 0 auto; width: 2.65rem; height: 2.65rem; align-items: center; justify-content: center; }
.whale-menu-icon img { width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 2px 3px #0005); transition: transform .25s ease; }
:global(button:hover) .whale-menu-icon img { transform: translateY(-2px); }
@media (prefers-reduced-motion: reduce) { .whale-menu-icon img { transition: none; } }
</style>
