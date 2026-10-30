<template>
  <div class="watch-page">
    <div v-if="isLoading" class="status-panel">正在加载剧集…</div>
    <div v-else-if="loadError" class="status-panel" role="alert"><p>{{ loadError }}</p><button class="secondary-button" @click="loadDrama">重新加载</button><RouterLink to="/">返回首页</RouterLink></div>
    <template v-else-if="drama">
      <div class="watch-breadcrumb"><RouterLink to="/"><AppIcon name="back" />返回发现</RouterLink><span>/</span><span>{{ drama.title }}</span></div>
      <div class="watch-layout">
        <section class="player-section" aria-label="视频播放">
          <div class="video-stage" :class="{ 'is-portrait': isPortrait }">
            <video ref="videoElement" controls playsinline preload="metadata" :poster="drama.backdrop" @loadedmetadata="restoreProgress" @loadeddata="updateAspect" @resize="updateAspect" @timeupdate="handleTimeUpdate" @ended="handleEnded" @error="handleVideoError" @playing="handlePlaying" @waiting="handleWaiting" @pause="persistProgress"></video>
            <div v-if="isResolving" class="video-overlay"><span class="loading-ring"></span><span>正在连接片源…</span></div>
            <div v-else-if="playError" class="video-overlay video-error" role="alert"><AppIcon name="film" /><p>{{ playError }}</p><button class="primary-button" @click="retryPlayback">重新播放</button></div>
            <div v-else-if="isBuffering" class="buffering"><span class="loading-ring"></span></div>
          </div>
          <div class="player-toolbar"><span class="playing-label"><i></i>{{ currentEpisode?.title || drama.title }}</span><div class="toolbar-actions"><ElSelect :suffix-icon="WhaleCaret" v-model="sourceIndex" aria-label="播放画质" :disabled="playbackUrls.length < 2" class="quality-select" :teleported="false"><ElOption v-for="(url, index) in playbackUrls" :key="url" :label="playbackLabels[index] || '原画'" :value="index" /></ElSelect><ElSelect :suffix-icon="WhaleCaret" v-model="playbackRate" aria-label="播放速度" class="speed-select" :teleported="false"><ElOption v-for="rate in [0.75, 1, 1.25, 1.5, 2]" :key="rate" :label="`${rate}倍速`" :value="rate" /></ElSelect><button :disabled="!hasNextEpisode" @click="goToEpisode(episodeNumber + 1)"><AppIcon name="next" /><span>下一集</span></button></div></div>
          <div class="drama-info"><div class="drama-title-line"><h1>{{ drama.title }}</h1><button class="secondary-button" :aria-pressed="savedIds.includes(drama.id)" @click="toggleSaved(drama.id)"><AppIcon :name="savedIds.includes(drama.id) ? 'check' : 'plus'" />{{ savedIds.includes(drama.id) ? '已加入片单' : '加入片单' }}</button></div><div class="drama-meta"><span v-if="drama.year">{{ drama.year }}</span><i></i><span>{{ drama.category }}</span><i></i><span>{{ drama.compilation ? (drama.originalEpisodeCount > 1 ? `全集版 · ${drama.originalEpisodeCount}集` : '全集版') : drama.status }}</span><i></i><span>{{ drama.duration ? `${Math.ceil(drama.duration / 60)}分钟` : drama.sourceName }}</span></div><p v-if="drama.synopsis" class="synopsis">{{ drama.synopsis }}</p><div class="detail-tags"><span v-for="tag in drama.tags" :key="tag">{{ tag }}</span></div></div>
          <details class="credits"><summary>片源与授权</summary><p>{{ drama.credit }}</p><p><a :href="licenseUrl" target="_blank" rel="noreferrer">{{ drama.licenseName }}</a></p><a :href="drama.sourcePage" target="_blank" rel="noreferrer">查看片源<AppIcon name="arrow" /></a></details>
        </section>
        <aside class="episodes-panel" aria-label="选集"><div class="episodes-heading"><h2>{{ drama.compilation ? '全集' : '选集' }}</h2><span>{{ drama.compilation ? '合集播放' : `${episodes.length}集` }}</span></div><div v-if="episodes.length > 1" class="autoplay-row"><span>自动连播</span><ElSwitch v-model="isAutoNext" aria-label="自动连播" /></div><div class="episode-list"><button v-for="episode in episodes" :key="episode.number" :class="{ active: episode.number === episodeNumber }" :aria-pressed="episode.number === episodeNumber" @click="goToEpisode(episode.number)"><span class="episode-number">{{ String(episode.number).padStart(2, '0') }}</span><span class="episode-name">{{ episode.title }}</span><span v-if="episode.number === episodeNumber" class="sound-bars"><i></i><i></i><i></i></span><span v-else-if="episode.duration" class="episode-duration">{{ Math.ceil(episode.duration / 60) }}分钟</span></button></div><p v-if="isFinished" class="finished-label">本片已看完</p><div class="aside-recommendations"><h2>接着发现</h2><RouterLink v-for="item in recommendations.slice(0, 3)" :key="item.id" :to="`/watch/${item.id}`"><img @error="handleImageError" :src="item.backdrop" :alt="item.title" loading="lazy" /><div><h3>{{ item.title }}</h3><span>{{ item.category }} · {{ item.compilation ? '全集版' : item.status }}</span></div><AppIcon name="chevron" /></RouterLink></div></aside>
      </div>
      <section v-if="recommendations.length" class="more-section"><h2>你可能还想看</h2><div class="recommendation-grid"><DramaCard v-for="item in recommendations.slice(0, 6)" :key="item.id" :drama="item" /></div></section>
    </template>
  </div>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElSelect, ElOption } from 'element-plus/es/components/select/index';
import { ElSwitch } from 'element-plus/es/components/switch/index';
import 'element-plus/theme-chalk/el-switch.css';
import 'element-plus/theme-chalk/el-select.css';
import 'element-plus/theme-chalk/el-option.css';
import 'element-plus/theme-chalk/el-popper.css';
import 'element-plus/theme-chalk/el-select-dropdown.css';
import 'element-plus/theme-chalk/el-scrollbar.css';
import Hls from 'hls.js';
import AppIcon from '../components/AppIcon.vue';
import WhaleCaret from '../components/WhaleCaret.vue';
import { handleImageError } from '../utils/image-fallback';
import DramaCard from '../components/DramaCard.vue';
import { getDrama, getDramas, getEpisodes, getPlayback } from '../api/dramas';
import { useCollection } from '../composables/use-collection';

const route = useRoute();
const router = useRouter();
const { savedIds, history, toggleSaved, saveProgress } = useCollection();
const drama = ref(null);
const episodes = ref([]);
const recommendations = ref([]);
const videoElement = ref(null);
const isLoading = ref(true);
const loadError = ref('');
const isResolving = ref(false);
const playError = ref('');
const isBuffering = ref(false);
const isFinished = ref(false);
const isPortrait = ref(false);
const playbackUrls = ref([]);
const playbackLabels = ref([]);
const sourceIndex = ref(0);
const playbackRate = ref(1);
const isAutoNext = ref(true);
const episodeNumber = computed(() => Number(route.params.episode || 1));
const currentEpisode = computed(() => episodes.value.find((episode) => episode.number === episodeNumber.value));
const licenseUrl = computed(() => {
  const match = drama.value?.licenseName.match(/^CC BY(-SA)? ([\d.]+)$/);
  return match ? `https://creativecommons.org/licenses/${match[1] ? 'by-sa' : 'by'}/${match[2]}/` : drama.value?.sourcePage;
});
const hasNextEpisode = computed(() => episodes.value.some((episode) => episode.number === episodeNumber.value + 1));
let hls = null;
let requestVersion = 0;
let lastSavedAt = 0;
let isChangingSource = false;
let pendingSeek = null;
let stallTimer = null;
let shouldAutoPlay = false;
let playingId = null;
let playingEpisode = 1;
let isSourcePortrait = false;

const destroyPlayer = () => {
  clearTimeout(stallTimer);
  hls?.destroy();
  hls = null;
  isSourcePortrait = false;
  const video = videoElement.value;
  if (video) { video.pause(); video.removeAttribute('src'); video.load(); }
};
const persistProgress = () => {
  const video = videoElement.value;
  if (video && drama.value && Number.isFinite(video.duration) && video.currentTime > 0 && !isChangingSource) {
    saveProgress(playingId || drama.value.id, playingEpisode, video.currentTime, video.duration);
  }
};
const handleTimeUpdate = () => {
  if (Date.now() - lastSavedAt < 3000) return;
  persistProgress();
  lastSavedAt = Date.now();
};
const attachSource = async () => {
  const video = videoElement.value;
  const url = playbackUrls.value[sourceIndex.value];
  if (!video || !url) return;
  hls?.destroy();
  hls = null;
  playError.value = '';
  isBuffering.value = true;
  clearTimeout(stallTimer);
  stallTimer = setTimeout(() => { if (isBuffering.value) { isBuffering.value = false; playError.value = '片源连接超时，请切换流畅画质或重试'; } }, 25000);
  if (/\.m3u8(?:\?|$)/i.test(url) && Hls.isSupported()) {
    hls = new Hls({ maxBufferLength: 30 });
    hls.loadSource(url);
    hls.attachMedia(video);
    hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) => {
      isSourcePortrait = data.levels.some((level) => level.height > level.width);
      updateAspect();
    });
    hls.on(Hls.Events.ERROR, (_event, data) => { if (data.fatal) handleVideoError(); });
  } else { video.src = url; video.load(); }
};
const restoreProgress = () => {
  const video = videoElement.value;
  if (!video || !drama.value) return;
  isPortrait.value = isSourcePortrait || video.videoHeight > video.videoWidth;
  clearTimeout(stallTimer);
  isBuffering.value = false;
  const previous = history.value.find((entry) => entry.id === drama.value.id && entry.episode === episodeNumber.value);
  const seek = pendingSeek ?? (previous && previous.time < video.duration - 5 ? previous.time : 0);
  pendingSeek = null;
  if (seek > 0 && Number.isFinite(video.duration)) video.currentTime = Math.min(seek, video.duration - 1);
  video.playbackRate = playbackRate.value;
  isChangingSource = false;
  if (shouldAutoPlay) {
    shouldAutoPlay = false;
    video.play().catch(() => { isBuffering.value = false; });
  }
};
const updateAspect = () => { if (videoElement.value?.videoWidth) isPortrait.value = isSourcePortrait || videoElement.value.videoHeight > videoElement.value.videoWidth; };
const handlePlaying = () => { updateAspect(); isBuffering.value = false; playError.value = ''; clearTimeout(stallTimer); };
const handleWaiting = () => {
  isBuffering.value = true;
  clearTimeout(stallTimer);
  stallTimer = setTimeout(() => {
    if (!isBuffering.value) return;
    if (sourceIndex.value > 0) {
      pendingSeek = videoElement.value?.currentTime || 0;
      shouldAutoPlay = true;
      sourceIndex.value = 0;
    } else { isBuffering.value = false; playError.value = '当前网络较慢，请稍后重试'; }
  }, 15000);
};
const handleVideoError = () => {
  if (isResolving.value || !playbackUrls.value.length || isChangingSource) return;
  isBuffering.value = false;
  if (sourceIndex.value + 1 < playbackUrls.value.length) {
    pendingSeek = videoElement.value?.currentTime || 0;
    shouldAutoPlay = true;
    sourceIndex.value += 1;
  } else { playError.value = '暂时无法播放，请检查网络后重试'; clearTimeout(stallTimer); }
};
const loadPlayback = async (autoPlay = true) => {
  const version = ++requestVersion;
  isResolving.value = true;
  isChangingSource = true;
  destroyPlayer();
  playbackUrls.value = [];
  playError.value = '';
  isFinished.value = false;
  pendingSeek = null;
  shouldAutoPlay = autoPlay;
  try {
    const data = await getPlayback(String(route.params.id), episodeNumber.value);
    if (version !== requestVersion) return;
    playbackUrls.value = data.urls;
    playbackLabels.value = data.labels || [];
    playingId = data.dramaId;
    playingEpisode = data.episode;
    sourceIndex.value = 0;
    await nextTick();
    await attachSource();
  } catch (failure) { if (version === requestVersion) playError.value = failure.message; }
  finally { if (version === requestVersion) { isResolving.value = false; isChangingSource = false; } }
};
const retryPlayback = () => loadPlayback();
const goToEpisode = (number) => {
  if (number === episodeNumber.value) return;
  persistProgress();
  router.push(`/watch/${drama.value.id}/${number}`);
};
const handleEnded = () => {
  persistProgress();
  isFinished.value = true;
  if (isAutoNext.value && hasNextEpisode.value) goToEpisode(episodeNumber.value + 1);
};
const loadDrama = async () => {
  persistProgress();
  isChangingSource = true;
  isLoading.value = true;
  loadError.value = '';
  const id = String(route.params.id);
  const version = ++requestVersion;
  destroyPlayer();
  try {
    if (/^(ff|wj)-/.test(id)) throw new Error('该片源已停用，请返回首页选择暴风或360资源');
    const source = /^(bf|zy360)-/.test(id) ? id.split('-')[0] : 'open';
    const [detail, list, catalog] = await Promise.all([getDrama(id), getEpisodes(id), getDramas({ source })]);
    if (version !== requestVersion) return;
    drama.value = detail;
    episodes.value = list;
    recommendations.value = catalog.filter((item) => item.id !== id);
    isLoading.value = false;
    await nextTick();
    const previous = history.value.find((entry) => entry.id === id);
    if (!route.params.episode && previous?.episode > 1 && list.some((item) => item.number === previous.episode)) {
      router.replace(`/watch/${id}/${previous.episode}`);
      return;
    }
    await loadPlayback();
  } catch (failure) { if (version === requestVersion) { loadError.value = failure.message; isLoading.value = false; } }
};
watch(sourceIndex, () => {
  if (isResolving.value || !videoElement.value || !playbackUrls.value.length) return;
  pendingSeek ??= videoElement.value.currentTime;
  shouldAutoPlay ||= !videoElement.value.paused;
  attachSource();
});
watch(playbackRate, (rate) => { if (videoElement.value) videoElement.value.playbackRate = rate; });
watch(() => route.params.id, loadDrama, { immediate: true });
watch(() => route.params.episode, () => { if (!isLoading.value && drama.value?.id === route.params.id) loadPlayback(); });
onBeforeUnmount(() => { persistProgress(); requestVersion += 1; isChangingSource = true; destroyPlayer(); });
</script>
<style scoped>
.watch-page { width: 91.2%; max-width: 1600px; margin: 0 auto; }
.watch-breadcrumb { display: flex; gap: 1rem; align-items: center; padding: 1.6rem 0; color: var(--subtle); font-size: .75rem; }
.watch-breadcrumb a { display: flex; align-items: center; gap: .4rem; color: var(--muted); }
.watch-breadcrumb a:hover { color: var(--accent); }
.watch-breadcrumb .whale-icon { width: 1.8rem; height: 1.8rem; }
.watch-layout { display: grid; grid-template-columns: minmax(0, 1fr) 20rem; gap: 1.5rem; align-items: start; }
.player-section { min-width: 0; }
.video-stage { aspect-ratio: 16 / 9; position: relative; background: #000; border-radius: .5rem .5rem 0 0; overflow: hidden; }
.video-stage video { width: 100%; height: 100%; object-fit: contain; }
@media (max-width: 800px) { .video-stage.is-portrait { aspect-ratio: 9 / 16; max-height: 72vh; } .drama-title-line { align-items: flex-start; } .drama-title-line button { flex-shrink: 0; } }
@media (max-width: 800px) { .video-stage.is-portrait video { object-fit: cover; } }
.video-overlay { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 1rem; background: #080b08e8; color: var(--muted); font-size: .85rem; }
.video-error > .whale-icon { width: 4rem; height: 4rem; color: var(--subtle); }
.video-error p { margin: .5rem 1rem; text-align: center; }
.buffering { position: absolute; inset: 0; display: grid; place-items: center; pointer-events: none; }
.loading-ring { display: block; width: 2rem; height: 2rem; border: 2px solid #ffffff20; border-top-color: var(--accent); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.player-toolbar { min-height: 3.5rem; background: #1b1d18; border: 1px solid var(--line); border-top: 0; border-radius: 0 0 .5rem .5rem; padding: .5rem 1rem; display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
.playing-label { display: flex; align-items: center; gap: .6rem; font-size: .75rem; color: var(--muted); }
.playing-label i { width: .3rem; height: .3rem; border-radius: 50%; background: var(--accent); }
.toolbar-actions { display: flex; align-items: center; gap: .7rem; }
.toolbar-actions button { display: flex; align-items: center; gap: .4rem; font-size: .7rem; color: var(--muted); white-space: nowrap; }
.toolbar-actions button:hover:not(:disabled) { color: var(--accent); }
.toolbar-actions .whale-icon { width: 1.8rem; height: 1.8rem; }
.quality-select { width: 4.7rem; }
.speed-select { width: 5.3rem; }
.toolbar-actions :deep(.el-select__wrapper) { font-size: .7rem; box-shadow: none; background: transparent; }
.drama-info { margin-top: 1.8rem; }
.drama-title-line { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
.drama-title-line h1 { font-family: inherit; font-size: 1.55rem; font-weight: 500; letter-spacing: .04em; margin: 0; }
.drama-title-line button { font-size: .75rem; padding: .6rem .8rem; }
.drama-meta { display: flex; flex-wrap: wrap; align-items: center; gap: .8rem; color: var(--muted); font-size: .7rem; margin-top: 1.1rem; }
.drama-meta i { width: 2px; height: 2px; background: var(--subtle); border-radius: 50%; }
.synopsis { color: var(--muted); font-size: .8rem; line-height: 1.95; max-width: 47rem; margin: 1.3rem 0; }
.detail-tags { display: flex; gap: .5rem; }
.detail-tags span { font-size: .65rem; border: 1px solid var(--line); color: var(--muted); padding: .3rem .6rem; border-radius: .2rem; }
.credits { margin-top: 1.5rem; border-top: 1px solid var(--line); padding: 1.2rem 0; font-size: .7rem; color: var(--subtle); line-height: 1.9; }
.credits summary { cursor: pointer; width: fit-content; }
.credits a { display: flex; align-items: center; gap: .4rem; color: var(--accent); width: fit-content; }
.credits .whale-icon { width: 1.6rem; height: 1.6rem; }
.episodes-panel { border: 1px solid var(--line); border-radius: .5rem; background: #181a15; padding: 1.4rem; }
.episodes-heading { display: flex; justify-content: space-between; align-items: center; }
.episodes-heading h2, .aside-recommendations h2 { font-size: 1rem; font-weight: 500; margin: 0; }
.episodes-heading > span { color: var(--subtle); font-size: .7rem; }
.autoplay-row { display: flex; align-items: center; justify-content: space-between; font-size: .72rem; color: var(--muted); margin: 1rem 0; padding-bottom: .8rem; border-bottom: 1px solid var(--line); }
.autoplay-row :deep(.el-switch) { --el-switch-on-color: #a6cd44; }
.episode-list { display: flex; flex-direction: column; gap: .5rem; max-height: 25rem; overflow-y: auto; margin-top: 1rem; }
.episode-list button { padding: .85rem .65rem; border: 1px solid var(--line); border-radius: .35rem; display: flex; align-items: center; gap: .75rem; text-align: left; }
.episode-list button.active { background: #d2f86b0a; border-color: #d2f86b45; color: var(--accent); }
.episode-list button:hover { border-color: var(--accent); }
.episode-number { color: var(--subtle); font-size: .8rem; }
.active .episode-number { color: var(--accent); }
.episode-name { font-size: .7rem; flex: 1; line-height: 1.5; }
.episode-duration { font-size: .6rem; color: var(--subtle); white-space: nowrap; }
.sound-bars { display: flex; align-items: center; gap: 2px; height: .8rem; }
.sound-bars i { width: 2px; height: 100%; background: var(--accent); }
.sound-bars i:nth-child(2) { height: 50%; }.sound-bars i:nth-child(3) { height: 70%; }
.finished-label { font-size: .7rem; color: var(--accent); }
.aside-recommendations { margin-top: 2.2rem; }
.aside-recommendations h2 { margin-bottom: 1rem; font-size: .8rem; }
.aside-recommendations > a { display: flex; gap: .7rem; align-items: center; margin-top: .9rem; }
.aside-recommendations img { width: 5.8rem; height: 3.6rem; object-fit: cover; border-radius: .3rem; }
.aside-recommendations h3 { font-size: .75rem; font-weight: 500; margin: 0 0 .5rem; }
.aside-recommendations span { font-size: .6rem; color: var(--subtle); }
.aside-recommendations > a > .whale-icon { margin-left: auto; width: 1.8rem; color: var(--subtle); }
.aside-recommendations > a:hover h3 { color: var(--accent); }
.more-section { margin-top: 2rem; }
.more-section > h2 { font-size: 1.2rem; margin: 0 0 1.5rem; font-weight: 500; }
.recommendation-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 1.3rem; }
@media (max-width: 1100px) { .watch-layout { grid-template-columns: minmax(0, 1fr) 17rem; gap: 1rem; } .episodes-panel { padding: 1rem; } .recommendation-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
@media (max-width: 800px) { .watch-layout { grid-template-columns: 1fr; } .episodes-panel { margin-top: .5rem; } .aside-recommendations { display: none; } .episode-list { display: grid; grid-template-columns: repeat(2, 1fr); } .player-toolbar { padding: .4rem .6rem; } .playing-label { max-width: 45%; font-size: .65rem; } .toolbar-actions { gap: .1rem; } .toolbar-actions button span { display: none; } .quality-select { width: 4.3rem; } .speed-select { width: 4.7rem; } .drama-title-line h1 { font-size: 1.6rem; } .drama-title-line button { font-size: .65rem; } .recommendation-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.5rem 1rem; } .watch-page { width: 90%; } }
</style>
