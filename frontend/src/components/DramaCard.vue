<template>
  <article class="drama-card">
    <RouterLink class="poster-link" :to="`/watch/${drama.id}`" :aria-label="`播放${drama.title}`">
      <img :src="drama.poster" :alt="drama.title" loading="lazy" @error="handleImageError" />
      <span class="complete-tag">{{ drama.compilation ? '全集版' : drama.status || `全${drama.episodeCount}集` }}</span>
      <span class="poster-shade"></span>
      <span class="poster-title">{{ drama.title }}</span>
      <span class="card-play"><AppIcon name="play" /></span>
      <span class="poster-bottom"><span>{{ drama.category }}</span><span>{{ drama.duration ? formatDuration(drama.duration) : drama.originalEpisodeCount > 1 ? `${drama.originalEpisodeCount}集` : '合集' }}</span></span>
    </RouterLink>
    <div class="card-heading"><RouterLink :to="`/watch/${drama.id}`">{{ drama.title }}</RouterLink><button :class="{ saved: savedIds.includes(drama.id) }" :aria-label="savedIds.includes(drama.id) ? `取消收藏${drama.title}` : `收藏${drama.title}`" :aria-pressed="savedIds.includes(drama.id)" @click="toggleSaved(drama.id)"><AppIcon :name="savedIds.includes(drama.id) ? 'check' : 'plus'" /></button></div>
    <div class="card-meta"><span>{{ drama.tags.slice(0, 2).join(' · ') }}</span><span>{{ drama.year || drama.sourceName }}</span></div>
  </article>
</template>
<script setup>
import AppIcon from './AppIcon.vue';
import { useCollection } from '../composables/use-collection';
import { handleImageError } from '../utils/image-fallback';
defineProps({ drama: { type: Object, required: true } });
const { savedIds, toggleSaved } = useCollection();
const formatDuration = (duration) => `${Math.ceil(duration / 60)}分钟`;
</script>
<style scoped>
.drama-card { min-width: 0; transition: transform .35s cubic-bezier(.2,.75,.3,1); animation: card-appear .45s ease both; }
@keyframes card-appear { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: translateY(0); } }
.drama-card:hover { transform: translateY(-5px); }
.poster-link { display: block; position: relative; aspect-ratio: 3 / 3.8; overflow: hidden; border-radius: .5rem; background: var(--panel); isolation: isolate; box-shadow: inset 0 0 0 1px #ffffff16; }
.poster-link::after { content: ''; position: absolute; inset: 0; border: 1px solid #ffffff16; border-radius: inherit; pointer-events: none; transition: border-color .3s; }
.poster-link:hover::after { border-color: #d2f86b80; }
.poster-link > img { width: 100%; height: 100%; object-fit: cover; transition: transform .65s cubic-bezier(.2,.75,.3,1), filter .4s; filter: saturate(.8); }
.poster-link:hover > img { transform: scale(1.07); filter: saturate(1.1); }
.poster-shade { position: absolute; inset: 0; background: linear-gradient(0deg, #080a05ed, #080a0500 80%); }
.complete-tag { position: absolute; z-index: 1; top: .65rem; right: .65rem; padding: .25rem .45rem; background: #10140dcc; border: 1px solid #ffffff26; border-radius: .2rem; color: #ffffffdd; font-size: .6rem; backdrop-filter: blur(8px); }
.poster-title { position: absolute; left: 1rem; bottom: 2.9rem; max-width: 85%; font-family: inherit; font-weight: 600; font-size: clamp(1rem, 1.2vw, 1.2rem); letter-spacing: .03em; line-height: 1.45; text-shadow: 0 2px 15px #0008; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.poster-bottom { position: absolute; bottom: 1rem; left: 1rem; right: 1rem; display: flex; justify-content: space-between; font-size: .64rem; color: #e4e8d4c4; }
.card-play { position: absolute; left: 50%; top: 40%; transform: translate(-50%, -40%) scale(.8); display: grid; place-items: center; width: 3rem; height: 3rem; border-radius: 50%; background: var(--accent); color: var(--bg); opacity: 0; transition: opacity .25s, transform .25s; }
.card-play .whale-icon { width: 2.6rem; height: 2.6rem; }
.poster-link:hover .card-play, .poster-link:focus-visible .card-play { opacity: 1; transform: translate(-50%, -40%) scale(1); }
.card-heading { display: flex; align-items: center; justify-content: space-between; margin-top: .75rem; gap: .25rem; }
.card-heading a { font-size: .9rem; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-heading a:hover { color: var(--accent); }
.card-heading button { display: grid; place-items: center; padding: .4rem; margin-right: -.4rem; color: var(--subtle); }
.card-heading button:hover, .card-heading .saved { color: var(--accent); }
.card-heading button:hover .whale-icon { transform: translateY(-2px); }
.card-heading .saved:hover .whale-icon { transform: scale(1.15); }
.card-heading button .whale-icon { transition: transform .3s; }
.card-heading .whale-icon { width: 1.8rem; height: 1.8rem; }
.card-meta { display: flex; justify-content: space-between; color: var(--subtle); font-size: .68rem; gap: .5rem; margin-top: .3rem; }
@media (max-width: 700px) { .poster-title { font-size: 1.1rem; } .card-heading a { font-size: .8rem; } }
</style>
