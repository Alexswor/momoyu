<template>
  <div class="discover-page">
    <template v-if="isLoading"><div class="hero-skeleton"></div><div class="content-wrap skeleton-grid"><div v-for="n in 6" :key="n"></div></div></template>
    <div v-else-if="error" class="status-panel" role="alert"><p>{{ error }}</p><button class="secondary-button" @click="loadCatalog">重新加载</button><button v-if="selectedSource !== 'zy360'" class="secondary-button" @click="selectSource('zy360')">切换360资源</button><button v-if="selectedSource !== 'wj'" class="secondary-button" @click="selectSource('wj')">切换无尽资源</button></div>
    <template v-else>
      <section v-if="isHome && featured" class="hero" aria-label="精选推荐" aria-roledescription="轮播" @mouseenter="isHovered = true" @mouseleave="isHovered = false" @focusin="hasFocus = true" @focusout="onFocusOut">
        <Transition name="hero-slide"><img @error="handleImageError" :key="featured.id" class="hero-image" :src="featured.backdrop" :alt="featured.title" fetchpriority="high" /></Transition>
        <div class="hero-gradient"></div>
        <div class="hero-frame" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
        <div class="hero-content">
          <div class="hero-label"><span></span>近期漫剧<i></i>{{ featured.compilation ? '全集版' : featured.status }}</div>
          <h1 :key="featured.id" :aria-label="featured.title"><span v-for="(character, index) in featured.title" :key="index" class="hero-character" aria-hidden="true">{{ character }}</span></h1>
          <div class="hero-meta"><span v-if="featured.year">{{ featured.year }}</span><i></i><span>{{ featured.category }}</span><i></i><span>{{ featured.originalEpisodeCount > 1 ? `全${featured.originalEpisodeCount}集` : featured.status }}</span><span class="hero-quality">{{ featured.compilation ? '合集' : '高清' }}</span></div>
          <div class="hero-tags"><span v-for="tag in featured.tags" :key="tag">{{ tag }}</span></div>
          <div class="hero-actions"><RouterLink class="primary-button" :to="`/watch/${featured.id}`"><AppIcon name="play" />立即观看</RouterLink><button class="secondary-button" :aria-pressed="savedIds.includes(featured.id)" @click="toggleSaved(featured.id)"><AppIcon :name="savedIds.includes(featured.id) ? 'check' : 'plus'" />{{ savedIds.includes(featured.id) ? '已加入片单' : '加入片单' }}</button></div>
        </div>
        <div class="hero-bottom"><div class="hero-pagination"><button v-for="(item, index) in heroDramas" :key="item.id" :class="{ current: heroIndex === index }" :aria-label="`推荐：${item.title}`" :aria-pressed="heroIndex === index" @click="selectSlide(index)"><span>{{ String(index + 1).padStart(2, '0') }}</span><i></i></button></div><div class="hero-filmstrip"><button v-for="(item, index) in heroDramas" :key="item.id" :class="{ active: heroIndex === index }" :aria-label="`切换至${item.title}`" :aria-pressed="heroIndex === index" @click="selectSlide(index)"><img @error="handleImageError" :src="item.backdrop" :alt="item.title" /><span>{{ item.title }}</span><i></i></button></div><div class="hero-arrows"><button v-if="!reducedMotion" :aria-label="isPaused ? '开启自动轮播' : '暂停自动轮播'" :aria-pressed="isPaused" @click="isPaused = !isPaused"><AppIcon :name="isPaused ? 'play' : 'pause'" /></button><button aria-label="上一个推荐" @click="changeSlide(-1)"><AppIcon name="back" /></button><button aria-label="下一个推荐" @click="changeSlide(1)"><AppIcon name="chevron" /></button></div></div>
        <div class="hero-caption"><UtilityIcon name="film" /><span>{{ featured.tags.includes('无对白') ? '无对白' : '原声' }}</span></div>
      </section>
      <div class="content-wrap">
        <div v-if="!isHome" class="page-heading"><h1>{{ pageTitle }}</h1><button v-if="isHistory && history.length" class="text-button" @click="handleClearHistory">清空记录</button></div>
        <section v-if="isHome || isLibrary" class="category-bar" aria-label="类型筛选"><button v-for="item in categories" :key="item.name" :class="{ selected: selectedCategory === item.name }" :aria-pressed="selectedCategory === item.name" @click="selectCategory(item.name)"><AppIcon :name="item.icon" />{{ item.name }}</button></section>
        <section v-if="isHome && continueWatching.length && selectedCategory === '全部'" class="continue-section">
          <div class="section-heading"><h2>接着看</h2><RouterLink to="/history">观看记录<WatchHistoryIcon /></RouterLink></div>
          <div class="continue-grid"><RouterLink v-for="item in continueWatching" :key="item.id" class="continue-card" :to="`/watch/${item.id}/${item.progress.episode}`"><img @error="handleImageError" :src="item.backdrop" :alt="item.title" /><div><h3>{{ item.title }}</h3><span>第{{ item.progress.episode }}集 · 已看{{ Math.floor(item.progress.time / 60) }}分钟</span><progress :value="item.progress.time" :max="item.progress.duration || item.duration"></progress></div><AppIcon name="play" /></RouterLink></div>
        </section>
        <section class="catalog-section">
          <div class="section-heading"><div class="heading-group"><h2>{{ isHome ? (selectedCategory === '全部' ? '近期上新' : selectedCategory) : route.query.q ? `“${route.query.q}”` : '全部' }}</h2><span class="count-label">{{ visibleDramas.length }}部</span><span v-if="catalogIndex?.isPrefetching" class="catalog-sync" role="status" aria-label="正在载入完整片库"></span></div><div class="section-tools"><ElSelect :suffix-icon="SelectCaret" v-if="!isSaved && !isHistory" :model-value="selectedSource" aria-label="选择片源" class="sort-select source-select" :teleported="false" @update:model-value="selectSource"><ElOption label="360资源" value="zy360" /><ElOption label="无尽资源" value="wj" /><ElOption label="非凡资源" value="ff" /></ElSelect><ElSelect :suffix-icon="SelectCaret" v-if="!isHistory" v-model="sortBy" aria-label="剧集排序" class="sort-select" :teleported="false"><ElOption label="默认排序" value="featured" /><ElOption label="最近更新" value="newest" /></ElSelect><RouterLink v-if="isHome" :to="{ path: '/library', query: { source: selectedSource } }">查看全部<UtilityIcon name="arrow" /></RouterLink></div></div>
          <div v-if="visibleDramas.length" class="drama-grid"><DramaCard v-for="drama in displayedDramas" :key="drama.id" :drama="drama" /></div>
          <div v-if="!(isHome && selectedCategory === '全部') && dramas.length" ref="lazySentinel" class="lazy-sentinel" aria-hidden="true"><span v-if="displayedDramas.length < visibleDramas.length || catalogIndex?.isPrefetching" class="catalog-sync"></span></div>
          <div v-if="catalogIndex?.error && dramas.length" class="catalog-error" role="alert"><span>{{ catalogIndex.error }}</span><button @click="loadCatalog(true)">重新连接</button></div>
          <div v-if="!visibleDramas.length" class="empty-state"><img class="empty-whale" src="/whale/dafeiyu-chibi.png" alt="鲸鱼娘" /><h3>{{ isSaved ? '片单还空着' : isHistory ? '还没有观看记录' : '没有找到相关作品' }}</h3><RouterLink v-if="isSaved || isHistory" class="secondary-button" to="/">去发现</RouterLink><button v-else class="secondary-button" @click="resetFilters">查看全部</button></div>
        </section>
        <section v-if="isHome && selectedCategory === '全部' && spotlightDramas.length" class="spotlight-section"><div class="section-heading"><h2>全集放映</h2><RouterLink :to="{ path: '/library', query: { source: selectedSource } }">更多漫剧<AppIcon name="arrow" /></RouterLink></div><div class="spotlight-grid"><RouterLink v-for="drama in spotlightDramas" :key="drama.id" class="spotlight-card" :to="`/watch/${drama.id}`"><img @error="handleImageError" :src="drama.backdrop" :alt="drama.title" loading="lazy" /><div class="spotlight-shade"></div><div class="spotlight-content"><span>{{ drama.category }}<i></i>{{ drama.originalEpisodeCount > 1 ? `全${drama.originalEpisodeCount}集` : drama.status }}</span><h3>{{ drama.title }}</h3><span class="spotlight-watch">立即观看<AppIcon name="arrow" /></span></div><span class="spotlight-play"><AppIcon name="play" /></span></RouterLink></div></section>
      </div>
    </template>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElSelect, ElOption } from 'element-plus/es/components/select/index';
import { ElMessageBox } from 'element-plus/es/components/message-box/index';
import 'element-plus/theme-chalk/el-select.css';
import 'element-plus/theme-chalk/el-option.css';
import 'element-plus/theme-chalk/el-popper.css';
import 'element-plus/theme-chalk/el-select-dropdown.css';
import 'element-plus/theme-chalk/el-scrollbar.css';
import 'element-plus/theme-chalk/el-message-box.css';
import { animate, createTimeline, stagger } from 'animejs';
import AppIcon from '../components/AppIcon.vue';
import SelectCaret from '../components/SelectCaret.vue';
import UtilityIcon from '../components/UtilityIcon.vue';
import WatchHistoryIcon from '../components/WatchHistoryIcon.vue';
import { useHeroCarousel } from '../composables/use-hero-carousel';
import { handleImageError } from '../utils/image-fallback';
import DramaCard from '../components/DramaCard.vue';
import { getDrama } from '../api/dramas';
import { getCatalogIndex } from '../composables/use-catalog-index';
import { useCollection } from '../composables/use-collection';

const route = useRoute();
const router = useRouter();
const { savedIds, history, toggleSaved, clearHistory } = useCollection();
const dramas = ref([]);
const isLoading = ref(true);
const error = ref('');
const heroIndex = ref(0);
const sortBy = ref('featured');
const catalogIndex = shallowRef(null);
const renderLimit = ref(24);
const lazySentinel = ref(null);
let lazyObserver = null;
let catalogRequest = 0;
let entranceAnimation = null;
let heroAnimation = null;
let cardAnimation = null;
const selectedCategory = computed(() => String(route.query.category || '全部'));
const selectedSource = computed(() => String(route.query.source || 'zy360'));
const isHome = computed(() => route.path === '/');
const isLibrary = computed(() => route.path === '/library');
const isSaved = computed(() => route.path === '/saved');
const isHistory = computed(() => route.path === '/history');
const pageTitle = computed(() => isSaved.value ? '我的片单' : isHistory.value ? '观看记录' : route.query.q ? '搜索结果' : '全部漫剧');
const categories = [ { name: '全部', icon: 'grid' }, { name: '玄幻', icon: 'sparkle' }, { name: '末世', icon: 'bolt' }, { name: '都市', icon: 'sun' }, { name: '古风', icon: 'compass' }, { name: '逆袭', icon: 'heart' } ];
const heroDramas = computed(() => dramas.value.slice(0, 4));
const { isPaused, isHovered, hasFocus, reducedMotion, selectSlide, changeSlide, onFocusOut } = useHeroCarousel(
  heroIndex, computed(() => heroDramas.value.length), computed(() => isHome.value && !isLoading.value && !error.value),
);
const featured = computed(() => heroDramas.value[heroIndex.value] || dramas.value[0]);
const spotlightDramas = computed(() => dramas.value.filter((item) => item.compilation).slice(4, 6));
const continueWatching = computed(() => history.value.slice(0, 3).map((progress) => ({ ...dramas.value.find((item) => item.id === progress.id), progress })).filter((item) => item.id));
const visibleDramas = computed(() => {
  const query = String(route.query.q || '').toLocaleLowerCase();
  const items = dramas.value.filter((drama) => {
    if (isSaved.value && !savedIds.value.includes(drama.id)) return false;
    if (isHistory.value && !history.value.some((item) => item.id === drama.id)) return false;
    if (selectedCategory.value !== '全部' && drama.category !== selectedCategory.value) return false;
    return !query || drama.sourceName !== '开放短片' || `${drama.title} ${drama.tags.join(' ')}`.toLocaleLowerCase().includes(query);
  });
  if (isHistory.value) return items.sort((a, b) => history.value.findIndex((item) => item.id === a.id) - history.value.findIndex((item) => item.id === b.id));
  if (sortBy.value === 'newest') return items.sort((a, b) => (b.updatedAt || String(b.year)).localeCompare(a.updatedAt || String(a.year)));
  if (sortBy.value === 'duration') return items.sort((a, b) => a.duration - b.duration);
  return items;
});
const displayedDramas = computed(() => isHome.value && selectedCategory.value === '全部' ? visibleDramas.value.slice(0, 6) : visibleDramas.value.slice(0, renderLimit.value));
const selectCategory = (name) => router.replace({ path: route.path, query: { ...route.query, category: name === '全部' ? undefined : name } });
const selectSource = (source) => router.replace({ path: route.path, query: { ...route.query, source, category: undefined } });
const resetFilters = () => router.replace({ path: '/library' });
const handleClearHistory = async () => {
  try { await ElMessageBox.confirm('清空本机的全部观看记录？', '清空记录', { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' }); clearHistory(); } catch { /* User cancelled. */ }
};
const revealCards = async () => {
  await nextTick();
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  cardAnimation?.revert();
  cardAnimation = animate('.drama-card', { opacity: [0, 1], translateY: [24, 0], duration: 650, delay: stagger(65), ease: 'outCubic' });
};
const revealHero = async () => {
  await nextTick();
  if (!isHome.value || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  heroAnimation?.revert();
  heroAnimation = createTimeline({ defaults: { ease: 'outExpo' } })
    .add('.hero-character', { opacity: [0, 1], translateY: ['80%', '0%'], rotate: [6, 0], duration: 1000, delay: stagger(85) })
    .add('.hero-meta, .hero-tags', { opacity: [0, 1], translateY: [12, 0], duration: 650, delay: stagger(70) }, 200)
    .add('.hero-actions', { opacity: [0, 1], translateY: [14, 0], duration: 700 }, 330);
};
const loadCatalog = async (refresh = false) => {
  const request = ++catalogRequest;
  isLoading.value = true;
  error.value = '';
  renderLimit.value = 24;
  catalogIndex.value = null;
  heroIndex.value = 0;
  try {
    let items;
    if (isSaved.value || isHistory.value) {
      const ids = isSaved.value ? savedIds.value : history.value.map((entry) => entry.id);
      const results = await Promise.allSettled(ids.map(getDrama));
      items = results.filter((result) => result.status === 'fulfilled').map((result) => result.value);
    } else {
      const index = getCatalogIndex(selectedSource.value, String(route.query.q || ''), refresh === true);
      catalogIndex.value = index;
      await index.ready;
      items = index.items;
    }
    if (request !== catalogRequest) return;
    dramas.value = items;
  } catch (failure) { if (request === catalogRequest) error.value = failure.message; }
  finally { if (request === catalogRequest) isLoading.value = false; }
  await revealCards();
  await revealHero();
  if (isHome.value && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    entranceAnimation = animate('.hero-image', { scale: [1.08, 1], duration: 1800, ease: 'outCubic' });
  }
};
watch(() => catalogIndex.value?.items, (items) => { if (items && !isSaved.value && !isHistory.value) dramas.value = items; });
const connectLazyObserver = async () => {
  await nextTick();
  lazyObserver?.disconnect();
  if (!lazySentinel.value || (isHome.value && selectedCategory.value === '全部')) return;
  lazyObserver = new IntersectionObserver(([entry]) => {
    if (entry.isIntersecting && renderLimit.value < visibleDramas.value.length) renderLimit.value += 24;
  }, { rootMargin: '800px' });
  lazyObserver.observe(lazySentinel.value);
};
watch([lazySentinel, () => visibleDramas.value.length, renderLimit], connectLazyObserver);
watch(() => [route.path, route.query.q, route.query.source], () => loadCatalog());
watch(() => route.query.category, () => { renderLimit.value = 24; revealCards(); connectLazyObserver(); });
watch(heroIndex, revealHero);
onMounted(loadCatalog);
onBeforeUnmount(() => { catalogRequest += 1; lazyObserver?.disconnect(); entranceAnimation?.revert(); heroAnimation?.revert(); cardAnimation?.revert(); });
</script>
<style scoped>
.content-wrap { width: 91.2%; max-width: 1600px; margin: 0 auto; }
.hero { height: 35rem; width: 95.6%; max-width: 1680px; margin: 1.2rem auto 0; position: relative; overflow: hidden; isolation: isolate; background: #252e25; border-radius: .85rem; border: 1px solid #ffffff12; }
.hero-image { width: 87%; height: 100%; position: absolute; right: 0; top: 0; object-fit: cover; object-position: center 45%; }
.hero-gradient { position: absolute; inset: 0; background: linear-gradient(90deg, #12160fee 1%, #12160fe0 19%, #12160f90 36%, #12160f00 65%), linear-gradient(0deg, #10140ff5, #10110f00 42%, #10110f00 75%, #10110f30); }
.hero-frame { position: absolute; inset: 1.2rem; pointer-events: none; z-index: 2; }
.hero-frame > span { position: absolute; width: .6rem; height: .6rem; border-color: #ffffff50; border-style: solid; }
.hero-frame > span:nth-child(1) { left: 0; top: 0; border-width: 1px 0 0 1px; }
.hero-frame > span:nth-child(2) { right: 0; top: 0; border-width: 1px 1px 0 0; }
.hero-frame > span:nth-child(3) { left: 0; bottom: 0; border-width: 0 0 1px 1px; }
.hero-frame > span:nth-child(4) { right: 0; bottom: 0; border-width: 0 1px 1px 0; }
.hero-content { position: relative; z-index: 1; padding-top: 5rem; margin-left: 4.4%; width: 48%; }
.hero-label { display: flex; align-items: center; gap: .6rem; color: var(--accent); font-size: .72rem; letter-spacing: .08em; }
.hero-label > span { width: .35rem; height: .35rem; border-radius: 50%; background: var(--accent); box-shadow: 0 0 12px #d2f86b66; }
.hero-label i { width: 1px; height: .6rem; background: #d2f86b50; margin: 0 .15rem; }
.hero h1 { font-family: inherit; font-size: clamp(2.5rem, 4vw, 3.8rem); font-weight: 600; line-height: 1.35; letter-spacing: .08em; margin: 1.8rem 0 1.2rem; text-shadow: 0 4px 40px #0005; overflow: hidden; padding-bottom: .08em; }
.hero-character { display: inline-block; transform-origin: bottom left; }
.hero-meta { display: flex; align-items: center; gap: .8rem; font-size: .75rem; color: #d7dacc; }
.hero-meta i { height: .7rem; width: 1px; background: #ffffff2b; }
.hero-quality { border: 1px solid #ffffff40; color: #d7dacc; font-size: .56rem; padding: .04rem .25rem; border-radius: .12rem; }
.hero-tags { display: flex; gap: .5rem; margin-top: 1rem; }
.hero-tags span { font-size: .66rem; color: var(--muted); }
.hero-tags span + span::before { content: '/'; margin-right: .5rem; color: #ffffff22; }
.hero-actions { display: flex; gap: .8rem; margin-top: 2.25rem; }
.hero-bottom { position: absolute; bottom: 2rem; left: 4.4%; right: 4.4%; display: flex; align-items: flex-end; justify-content: space-between; gap: 1.5rem; }
.hero-filmstrip { margin-left: auto; display: flex; gap: .65rem; }
.hero-filmstrip button { position: relative; width: 7.4rem; height: 4.7rem; border-radius: .35rem; border: 1px solid #ffffff30; padding: 0; overflow: hidden; opacity: .55; }
.hero-filmstrip img { width: 100%; height: 100%; object-fit: cover; transition: transform .45s; }
.hero-filmstrip button:hover { opacity: 1; }
.hero-filmstrip button:hover img { transform: scale(1.12); }
.hero-filmstrip button.active { border-color: var(--accent); opacity: 1; box-shadow: 0 0 0 3px #d2f86b16; }
.hero-filmstrip span { position: absolute; bottom: 0; left: 0; width: 100%; text-align: left; padding: .8rem .5rem .35rem; font-size: .58rem; background: linear-gradient(transparent, #000b); }
.hero-filmstrip i { position: absolute; bottom: 0; left: 0; width: 0; height: 2px; background: var(--accent); }
.hero-filmstrip .active i { width: 100%; transition: width .65s; }
.hero-pagination { display: flex; gap: 1.2rem; }
.hero-pagination button { display: flex; align-items: center; gap: .5rem; padding: .5rem 0; font-size: .65rem; color: #ffffff60; }
.hero-pagination i { width: 1.4rem; height: 2px; background: #ffffff20; }
.hero-pagination .current { color: var(--accent); }
.hero-pagination .current i { background: var(--accent); width: 3.1rem; }
.hero-arrows { display: flex; gap: .6rem; }
.hero-arrows button { border: 1px solid #ffffff30; width: 2.6rem; height: 2.6rem; border-radius: 50%; display: grid; place-items: center; background: #10110f30; }
.hero-arrows button:hover { background: var(--accent); color: var(--bg); border-color: var(--accent); }
.hero-arrows .whale-icon { width: 1.9rem; height: 1.9rem; }
.hero-caption { position: absolute; right: 4.4%; bottom: 8.2rem; display: flex; align-items: center; gap: .4rem; color: #ffffffb0; font-size: .62rem; }
.hero-caption .utility-icon { width: .9rem; height: .9rem; }
.hero-slide-enter-active, .hero-slide-leave-active { transition: opacity .65s ease, translate .65s ease; }
.hero-slide-enter-from { opacity: 0; translate: 5% 0; }
.hero-slide-leave-to { opacity: 0; translate: -5% 0; }
.category-bar { display: flex; align-items: center; padding: 1.6rem 0 1.9rem; gap: 1rem; border-bottom: 1px solid var(--line); overflow-x: auto; scrollbar-width: none; }
.category-bar button { display: flex; gap: .6rem; align-items: center; justify-content: center; min-width: 7.2rem; padding: .75rem 1.4rem; border-radius: .35rem; font-size: .78rem; color: var(--muted); white-space: nowrap; }
.category-bar button + button { margin-left: .2rem; }
.category-bar button:hover { color: var(--text); background: #ffffff07; }
.category-bar .selected { color: var(--accent); background: #d2f86b10; }
.category-bar .whale-icon { width: 2.8rem; height: 2.8rem; }
.catalog-section, .continue-section, .spotlight-section { margin-top: 2.4rem; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; gap: 1rem; }
.section-heading h2 { margin: 0; font-size: 1.08rem; font-weight: 500; letter-spacing: .03em; }
.heading-group { display: flex; align-items: center; gap: .9rem; }
.count-label { color: var(--subtle); font-size: .7rem; margin-top: .1rem; }
.section-heading a { display: flex; align-items: center; gap: .5rem; font-size: .7rem; color: var(--muted); white-space: nowrap; }
.section-heading a:hover { color: var(--accent); }
.section-heading a .whale-icon { width: 1.8rem; height: 1.8rem; }
.section-heading a .watch-history-icon { width: 2.2rem; height: 2.2rem; }
.section-tools { display: flex; align-items: center; gap: 1.8rem; }
.sort-select { width: 7rem; --el-select-input-font-size: .7rem; }
.source-select { width: 6.5rem; }
.lazy-sentinel { min-height: 5rem; display: grid; place-items: center; }
.catalog-sync { display: inline-block; width: .85rem; height: .85rem; border: 1px solid var(--line); border-top-color: var(--accent); border-radius: 50%; animation: catalog-spin 1s linear infinite; }
.catalog-error { display: flex; justify-content: center; gap: 1rem; padding: 1rem; font-size: .75rem; color: var(--muted); }
.catalog-error button { color: var(--accent); }
@keyframes catalog-spin { to { transform: rotate(360deg); } }
.sort-select :deep(.el-select__caret.el-icon) { width: 1.15rem; height: 1.15rem; }
.sort-select :deep(.el-select__wrapper) { background: transparent; box-shadow: none; padding: .3rem; }
.sort-select :deep(.el-select-dropdown__item.is-selected) { color: #799c28; }
.drama-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 2rem 1.3rem; }
.page-heading { display: flex; justify-content: space-between; align-items: center; padding: 2.5rem 0 1rem; }
.page-heading h1 { margin: 0; font-size: 1.55rem; font-weight: 500; }
.text-button { color: var(--muted); font-size: .8rem; }
.text-button:hover { color: var(--accent); }
.empty-state { display: flex; align-items: center; flex-direction: column; justify-content: center; min-height: 22rem; gap: 1rem; color: var(--muted); }
.empty-whale { width: 5.5rem; height: 5.5rem; object-fit: contain; opacity: .85; }
.empty-state h3 { font-size: 1rem; font-weight: 400; }
.spotlight-section { margin-top: 3rem; }
.spotlight-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.3rem; }
.spotlight-card { height: 15rem; border-radius: .5rem; overflow: hidden; position: relative; isolation: isolate; }
.spotlight-card > img { width: 100%; height: 100%; object-fit: cover; transition: transform .6s; }
.spotlight-card:hover > img { transform: scale(1.05); }
.spotlight-shade { position: absolute; inset: 0; background: linear-gradient(90deg, #0a100bd9, #0a100b00), linear-gradient(0deg, #0a100b90, transparent); }
.spotlight-content { position: absolute; left: 1.8rem; top: 2.4rem; }
.spotlight-content > span { display: flex; align-items: center; gap: .6rem; font-size: .68rem; color: #d7dacc; }
.spotlight-content i { width: 2px; height: 2px; border-radius: 50%; background: currentColor; }
.spotlight-content h3 { font-family: inherit; font-weight: 500; font-size: 1.5rem; margin: .8rem 0 1.4rem; letter-spacing: .04em; }
.spotlight-content .spotlight-watch { color: var(--accent); font-size: .72rem; }
.spotlight-play { width: 2.8rem; height: 2.8rem; border: 1px solid #ffffff55; border-radius: 50%; display: grid; place-items: center; position: absolute; right: 1.8rem; bottom: 1.8rem; backdrop-filter: blur(6px); }
.spotlight-play .whale-icon { width: 2.4rem; height: 2.4rem; }
.continue-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
.continue-card { display: flex; align-items: center; gap: 1rem; padding: .65rem; border: 1px solid var(--line); border-radius: var(--radius); background: #ffffff03; }
.continue-card > img { width: 5rem; height: 3.5rem; object-fit: cover; border-radius: .3rem; }
.continue-card > div { flex: 1; min-width: 0; }
.continue-card h3 { font-size: .8rem; margin: 0 0 .3rem; }
.continue-card span { font-size: .62rem; color: var(--muted); }
.continue-card progress { display: block; height: 3px; width: 100%; margin-top: .65rem; accent-color: var(--accent); }
.continue-card > .whale-icon { color: var(--accent); margin-right: .6rem; }
.hero-skeleton { height: 32.5rem; background: linear-gradient(100deg, var(--panel), #ffffff07, var(--panel)); background-size: 200% 100%; animation: loading 2s infinite; }
.skeleton-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 1.3rem; margin-top: 7rem; }
.skeleton-grid > div { aspect-ratio: 3 / 4; background: var(--panel); border-radius: .5rem; }
@keyframes loading { to { background-position: -200% 0; } }
@media (min-width: 1700px) { .hero-content { margin-left: max(4.4%, calc((100vw - 1600px) / 2)); } }
@media (max-width: 1100px) { .drama-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); } .category-bar { gap: .4rem; } .category-bar button { min-width: 6rem; padding: .7rem 1rem; } .hero { height: 32rem; } .hero-content { padding-top: 4.5rem; } .hero-filmstrip button { width: 5.5rem; height: 3.8rem; } .continue-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 700px) { .content-wrap { width: 90%; } .hero { height: 30rem; } .hero-image { width: 100%; object-position: 58% center; } .hero-gradient { background: linear-gradient(90deg, #10110fa0, #10110f05), linear-gradient(0deg, var(--bg) 2%, #10110fd9 28%, #10110f00 85%); } .hero-content { padding-top: 10rem; margin-left: 5%; width: 90%; } .hero-label { font-size: .63rem; } .hero h1 { font-size: 3.5rem; margin: 1rem 0; } .hero-meta { font-size: .65rem; } .hero-tags { margin-top: .7rem; } .hero-actions { margin-top: 1.3rem; gap: .6rem; } .primary-button, .secondary-button { font-size: .75rem; padding: .75rem 1.15rem; min-height: 2.8rem; } .hero-bottom { bottom: .9rem; left: 5%; right: 5%; } .hero-caption { display: none; } .hero-pagination { gap: .7rem; } .hero-pagination i { width: .8rem; } .hero-pagination .current i { width: 2rem; } .category-bar { padding: 1.2rem 0; gap: .4rem; } .category-bar button { min-width: auto; padding: .65rem .9rem; font-size: .7rem; } .category-bar .whale-icon { width: 2.25rem; height: 2.25rem; } .catalog-section { margin-top: 1.7rem; } .section-heading { margin-bottom: 1.2rem; } .section-heading h2 { font-size: 1.1rem; } .section-tools { gap: .7rem; } .section-heading a { font-size: .65rem; } .count-label { font-size: .6rem; } .drama-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.6rem 1rem; } .spotlight-grid { grid-template-columns: 1fr; } .spotlight-card { height: 12rem; } .continue-grid { grid-template-columns: 1fr; } .page-heading h1 { font-size: 1.6rem; } }
@media (max-width: 700px) { .hero { width: 100%; border-radius: 0; border: 0; margin-top: 0; } .hero-filmstrip { display: none; } .hero-frame { inset: .8rem; } .hero-content { padding-left: .3rem; } .hero h1 { font-size: 2.5rem; } .section-heading h2 { font-size: 1rem; } .page-heading h1 { font-size: 1.4rem; } }
@media (max-width: 700px) { .section-heading { flex-wrap: wrap; } .section-tools { gap: .5rem; } .hero h1 { font-size: 1.85rem; max-width: 95%; } .hero-content { padding-top: 8rem; } .spotlight-content h3 { font-size: 1.2rem; max-width: 13em; } }
</style>
