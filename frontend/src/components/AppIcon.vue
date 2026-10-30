<template>
  <span class="whale-icon" :data-icon="name" aria-hidden="true">
    <img class="whale-icon__figure" :src="`/whale/icons/${poses[name] || 'cinema'}.webp`" alt="" decoding="async" draggable="false" />
    <svg class="whale-icon__symbol" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path v-for="(path, index) in paths[name] || paths.play" :key="index" :d="path" />
    </svg>
  </span>
</template>
<script setup>
defineProps({ name: { type: String, default: 'play' } });
const poses = {
  search: 'search', play: 'cinema', pause: 'cinema', film: 'cinema', grid: 'cinema', list: 'scroll',
  sparkle: 'magic', bolt: 'storm', sun: 'city', compass: 'scroll',
  heart: 'love', plus: 'love', check: 'love', bookmark: 'love',
  clock: 'history', arrow: 'guide', chevron: 'guide', back: 'guide', next: 'guide',
  up: 'guide', down: 'guide', close: 'guide', volume: 'city',
};
const paths = {
  pause: ['M8 5v14M16 5v14'], down: ['m5 9 7 7 7-7'], up: ['m5 15 7-7 7 7'],
  play: ['M9 5l11 7-11 7V5Z'], search: ['M21 21l-4.5-4.5', 'M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0'],
  arrow: ['M5 12h14m-6-6 6 6-6 6'], chevron: ['m9 5 7 7-7 7'], back: ['m15 5-7 7 7 7'],
  plus: ['M12 5v14M5 12h14'], check: ['m5 12 4 4L19 6'], close: ['m6 6 12 12M6 18 18 6'],
  bookmark: ['M6 4h12v17l-6-4-6 4V4Z'], clock: ['M12 7v5l3 2', 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0'],
  grid: ['M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z'],
  sparkle: ['m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z'],
  sun: ['M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5', 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0'],
  bolt: ['m13 2-9 12h7l-1 8 10-13h-7l1-7Z'], heart: ['M20.8 4.6a5.4 5.4 0 0 0-7.6 0L12 5.8l-1.2-1.2a5.4 5.4 0 0 0-7.6 7.6L12 21l8.8-8.8a5.4 5.4 0 0 0 0-7.6Z'],
  compass: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0', 'm16 8-3 5-5 3 3-5 5-3Z'],
  next: ['M18 5v14M6 5l10 7-10 7V5Z'], volume: ['M11 4 6 8H3v8h3l5 4V4Z', 'M15 8c3 2 3 6 0 8m3-11c5 4 5 10 0 14'],
  film: ['M4 3h16v18H4Z', 'M8 3v18M16 3v18M4 8h4m8 0h4M4 16h4m8 0h4'],
  list: ['M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01'],
};
</script>
<style scoped>
.whale-icon { width: 2rem; height: 2rem; position: relative; flex: 0 0 auto; display: inline-block; vertical-align: middle; isolation: isolate; }
.whale-icon__figure { display: block; width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 1px 2px #0006); transition: transform .25s ease; }
.whale-icon[data-icon="back"] .whale-icon__figure { scale: -1 1; }
.whale-icon__symbol { position: absolute; right: -.12rem; bottom: -.08rem; width: .85rem; height: .85rem; padding: .1rem; color: var(--accent); background: #171b14ed; border: 1px solid #d2f86b38; border-radius: .3rem; }
:global(button:hover) .whale-icon__figure, :global(a:hover) .whale-icon__figure { transform: translateY(-2px); }
@media (prefers-reduced-motion: reduce) { .whale-icon__figure { transition: none; } }
</style>
