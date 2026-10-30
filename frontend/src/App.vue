<template>
  <a class="skip-link" href="#main-content">跳至内容</a>
  <header class="site-header">
    <RouterLink class="brand" to="/" aria-label="摸摸鱼首页"><img src="/whale/dafeiyu-chibi.png" alt="Q版鲸鱼娘" /><span>摸摸鱼</span></RouterLink>
    <nav class="main-nav" aria-label="主导航">
      <RouterLink to="/" exact-active-class="active"><AppIcon name="sparkle" />发现</RouterLink>
      <RouterLink to="/library" active-class="active"><AppIcon name="grid" />全部漫剧</RouterLink>
      <RouterLink to="/saved" active-class="active"><AppIcon name="heart" />我的片单</RouterLink>
    </nav>
    <form class="search-form" role="search" @submit.prevent="handleSearch">
      <button class="search-submit" type="submit" aria-label="搜索"><AppIcon name="search" /></button>
      <input v-model="keyword" aria-label="搜索剧名或类型" placeholder="搜索剧名、类型" maxlength="80" />
      <kbd>/</kbd>
    </form>
    <RouterLink class="history-link" to="/history" aria-label="观看记录"><AppIcon name="clock" /><span>观看记录</span></RouterLink>
  </header>
  <main id="main-content"><RouterView /></main>
  <WhaleCompanion />
  <footer class="site-footer"><RouterLink class="footer-brand" to="/">摸摸鱼</RouterLink><span>© {{ new Date().getFullYear() }} 摸摸鱼</span><button @click="showCredits = true">片源与授权</button><a href="https://github.com/juliangarnier/anime" target="_blank" rel="noreferrer">开源致谢<AppIcon name="arrow" /></a></footer>
  <ElDialog :close-icon="WhaleClose" v-model="showCredits" title="片源与授权" width="min(38rem, 92vw)" class="credits-dialog">
    <p>漫剧目录接入 360、无尽与非凡的公开采集接口。内容、集数与更新状态以片源返回为准，全集合并版会单独标记。</p>
    <p>播放页保留当前片源及查询链接。第三方目录可访问并不代表已获得内容的发行授权，片源失效时可在目录页切换来源。</p>
    <p>开放短片保留其原作者与许可声明。</p>
    <p><a href="https://treapgogo.github.io/deepseek-whale-girl/" target="_blank" rel="noreferrer">鲸鱼娘素材来源<AppIcon name="arrow" /></a></p>
  </ElDialog>
</template>
<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElDialog } from 'element-plus/es/components/dialog/index';
import 'element-plus/theme-chalk/base.css';
import 'element-plus/theme-chalk/el-dialog.css';
import 'element-plus/theme-chalk/el-overlay.css';
import 'element-plus/theme-chalk/el-message.css';
import AppIcon from './components/AppIcon.vue';
import WhaleCompanion from './components/WhaleCompanion.vue';
import WhaleClose from './components/WhaleClose.vue';

const keyword = ref('');
const route = useRoute();
const router = useRouter();
const showCredits = ref(false);
watch(() => route.query.q, (value) => { keyword.value = String(value || ''); }, { immediate: true });
const handleSearch = () => router.push({ path: '/library', query: { source: route.query.source, q: keyword.value.trim() || undefined } });
const handleShortcut = (event) => {
  if (event.key === '/' && !['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) {
    event.preventDefault();
    document.querySelector('.search-form input')?.focus();
  }
};
onMounted(() => document.addEventListener('keydown', handleShortcut));
onUnmounted(() => document.removeEventListener('keydown', handleShortcut));
</script>
<style>
@font-face { font-family: 'Chill Round'; src: url('/fonts/ChillRoundF.ttf') format('truetype'); font-weight: 400 700; font-style: normal; font-display: swap; }
:root { color-scheme: dark; --bg: #10110f; --panel: #1b1d18; --text: #f4f5ed; --muted: #a3a69a; --subtle: #777b6e; --accent: #d2f86b; --line: #ffffff12; --danger: #ff9b87; --radius: .85rem; --el-color-primary: var(--accent); --el-text-color-primary: var(--text); --el-text-color-regular: var(--muted); --el-bg-color: var(--panel); --el-bg-color-overlay: var(--panel); --el-border-color-lighter: var(--line); --el-fill-color-blank: var(--panel); font-family: 'Chill Round', 'YouYuan', '幼圆', 'PingFang SC', sans-serif; font-synthesis: none; }
* { box-sizing: border-box; }
html { min-width: 320px; scroll-behavior: smooth; }
body { margin: 0; background: var(--bg); color: var(--text); -webkit-font-smoothing: antialiased; }
button, input { font: inherit; }
button, a { -webkit-tap-highlight-color: transparent; }
button { color: inherit; cursor: pointer; border: 0; background: none; }
a { color: inherit; text-decoration: none; }
button:focus-visible, a:focus-visible, input:focus-visible { outline: 2px solid var(--accent); outline-offset: 5px; }
button:disabled { cursor: default; opacity: .4; }
button, a { transition: color .2s, background .2s, border-color .2s, opacity .2s, transform .2s; }
::selection { background: var(--accent); color: var(--bg); }
.site-header { height: 6rem; padding: 0 4.4%; display: flex; align-items: center; gap: 3.2rem; border-bottom: 1px solid var(--line); background: var(--bg); position: relative; z-index: 10; }
.brand { display: inline-flex; align-items: center; gap: .65rem; white-space: nowrap; }
.brand img { width: 2.5rem; height: 2.5rem; object-fit: contain; }
.search-submit { display: grid; place-items: center; padding: 0; flex-shrink: 0; }
.search-submit .whale-icon { width: 2.3rem; height: 2.3rem; }
.brand > span { font-size: 1.5rem; font-weight: 600; letter-spacing: .03rem; }
.main-nav { display: flex; align-items: stretch; gap: 2.2rem; align-self: stretch; white-space: nowrap; }
.main-nav a { display: flex; align-items: center; position: relative; color: var(--muted); font-size: .9rem; gap: .4rem; }
.main-nav a:hover, .main-nav a.active { color: var(--text); }
.main-nav a.active::after { content: ''; width: 1.15rem; height: .2rem; border-radius: 2rem; background: var(--accent); position: absolute; bottom: 1.25rem; left: 50%; transform: translateX(-50%); }
.search-form { margin-left: auto; width: 17rem; height: 3rem; display: flex; gap: .6rem; align-items: center; border: 1px solid var(--line); background: #ffffff05; border-radius: 2rem; padding: 0 .9rem; color: var(--muted); }
.search-form:focus-within { border-color: var(--accent); }
.search-form input { width: 100%; min-width: 0; border: 0; outline: 0; background: none; color: var(--text); font-size: .78rem; }
.search-form input::placeholder { color: var(--subtle); }
.search-form kbd { font-size: .75rem; color: var(--subtle); border: 1px solid var(--line); padding: .04rem .3rem; border-radius: .2rem; }
.history-link { display: flex; align-items: center; gap: .5rem; font-size: .8rem; color: var(--muted); margin-left: -.8rem; white-space: nowrap; }
.history-link:hover { color: var(--accent); }
.site-footer { display: flex; align-items: center; gap: 1.8rem; padding: 2.5rem 4.4%; border-top: 1px solid var(--line); margin-top: 3.5rem; color: var(--subtle); font-size: .73rem; }
.footer-brand { color: var(--muted); font-size: 1.25rem; font-weight: 800; margin-right: auto; }
.site-footer a:last-child { display: flex; align-items: center; gap: .4rem; }
.site-footer .whale-icon { width: 1.7rem; height: 1.7rem; }
.site-footer button:hover, .site-footer a:hover { color: var(--accent); }
.skip-link { position: absolute; top: -5rem; left: 1rem; z-index: 100; background: var(--accent); color: var(--bg); padding: .8rem; }
.skip-link:focus { top: 1rem; }
.primary-button { background: var(--accent); color: #1b2410; padding: .85rem 1.5rem; border-radius: 2rem; display: inline-flex; align-items: center; justify-content: center; gap: .6rem; font-size: .82rem; font-weight: 600; min-height: 2.8rem; position: relative; overflow: hidden; isolation: isolate; box-shadow: 0 0 0 1px #d2f86b10; }
.primary-button::before { content: ''; position: absolute; inset: -50% -100%; background: linear-gradient(110deg, transparent 40%, #ffffff70 50%, transparent 60%); transform: translateX(-45%); transition: transform .65s ease; z-index: -1; pointer-events: none; }
.primary-button:hover::before { transform: translateX(45%); }
.primary-button:hover { background: #e4ffa4; transform: translateY(-2px); box-shadow: 0 0 0 4px #d2f86b12, 0 8px 28px #bce44b18; }
.primary-button .whale-icon { width: 2rem; height: 2rem; }
.secondary-button { padding: .8rem 1.2rem; border: 1px solid #ffffff30; border-radius: 2rem; display: inline-flex; align-items: center; justify-content: center; gap: .5rem; background: #ffffff08; font-size: .78rem; }
.secondary-button:hover { border-color: var(--accent); color: var(--accent); background: #d2f86b0a; }
.status-panel { min-height: 20rem; display: grid; place-content: center; text-align: center; gap: 1.2rem; color: var(--muted); }
.status-panel button { justify-self: center; }
.poster-fallback { object-fit: contain !important; padding: 1.2rem; background: #181c15; }
.credits-dialog { border: 1px solid var(--line); border-radius: var(--radius); padding: 1.8rem; }
.el-select__caret.el-icon { width: 1.6rem; height: 1.6rem; transform: none !important; }
.el-select__caret .whale-icon { width: 1.6rem; height: 1.6rem; }
.el-select__caret.is-reverse .whale-icon__symbol { rotate: 180deg; }
.el-dialog__headerbtn { width: 2.6rem; height: 2.6rem; }
.credits-dialog p { line-height: 1.9; }
.credits-dialog a { color: var(--accent); display: inline-flex; gap: .5rem; align-items: center; }
@media (min-width: 1700px) { .site-header, .site-footer { padding-left: max(4.4%, calc((100vw - 1600px) / 2)); padding-right: max(4.4%, calc((100vw - 1600px) / 2)); } }
@media (max-width: 1000px) { .site-header { gap: 2rem; padding: 0 4%; height: 5.2rem; } .main-nav { gap: 1.5rem; } .search-form { width: 13rem; } .history-link span { display: none; } }
@media (max-width: 700px) { .site-header { height: 7.9rem; flex-wrap: wrap; gap: 0; padding: 1rem 5% .8rem; align-content: space-between; } .brand > span { font-size: 1.4rem; } .brand img { width: 1.7rem; height: 1.7rem; } .main-nav { order: 3; width: 100%; height: 2rem; justify-content: center; gap: 2.5rem; } .main-nav a { font-size: .83rem; gap: .3rem; } .main-nav .whale-icon { width: 1.7rem; height: 1.7rem; } .main-nav a.active::after { bottom: -.5rem; } .search-form { width: 45%; height: 2.2rem; } .search-form kbd { display: none; } .history-link { margin-left: 1rem; } .site-footer { flex-wrap: wrap; gap: 1rem; padding: 1.5rem 5%; } .footer-brand { width: 40%; } }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; scroll-behavior: auto !important; } }
</style>
