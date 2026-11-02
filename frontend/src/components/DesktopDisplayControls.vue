<template>
  <div v-if="nativeToken || isAvailable" class="display-dock" aria-label="窗口设置">
    <template v-if="nativeToken">
      <button class="display-toggle" :disabled="!windowState.ready" @click="change({ opacity: windowState.opacity < 100 ? 100 : 55, clickThrough: false, autoHide: false })"><UtilityIcon name="layers" />{{ windowState.opacity < 100 ? '恢复' : '半透明' }}</button>
      <label class="opacity-level"><span class="sr-only">窗口不透明度</span><input type="range" min="20" max="100" step="5" :value="windowState.opacity" :disabled="!windowState.ready" aria-label="窗口不透明度" @input="setOpacity($event.target.value)" /><output>{{ windowState.opacity }}%</output></label>
      <button class="settings-toggle" aria-label="小窗设置" :aria-expanded="isOpen" @click="isOpen = !isOpen"><UtilityIcon name="settings" /></button>
      <div v-if="isOpen" class="window-options">
        <button :aria-pressed="windowState.topmost" @click="change({ topmost: !windowState.topmost })"><UtilityIcon name="pin" />置顶</button>
        <button :aria-pressed="windowState.clickThrough" :disabled="!windowState.restoreKey" @click="change({ clickThrough: !windowState.clickThrough, autoHide: false })"><UtilityIcon name="cursor" />鼠标穿透</button>
        <button :aria-pressed="windowState.autoHide" :disabled="!windowState.restoreKey" @click="change({ autoHide: !windowState.autoHide, clickThrough: false })"><UtilityIcon name="eye" />移入隐藏</button>
        <span v-if="windowState.restoreKey" class="restore-hint">{{ windowState.restoreKey }} 恢复窗口</span>
        <span v-else class="restore-hint">恢复快捷键被占用</span>
      </div>
    </template>
    <button v-else class="display-toggle" :disabled="isOpening" @click="launch"><UtilityIcon name="layers" />{{ isOpening ? '正在打开…' : '半透明小窗' }}</button>
  </div>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index';
import UtilityIcon from './UtilityIcon.vue';
import { useDisplayMode, desktopRequest } from '../composables/use-display-mode';
const route = useRoute();
const { nativeToken, isAvailable, windowState, refreshWindow, updateWindow } = useDisplayMode();
const isOpen = ref(false);
const isOpening = ref(false);
let timer, opacityTimer;
const change = async (changes) => {
  try { await updateWindow(changes); }
  catch (error) { ElMessage.warning(error.message); }
};
const setOpacity = (value) => {
  clearTimeout(opacityTimer);
  const opacity = Number(value);
  windowState.value.opacity = opacity;
  opacityTimer = setTimeout(() => change({ opacity }), 120);
};
const launch = async () => {
  isOpening.value = true;
  try {
    const query = new URLSearchParams(route.query);
    for (const key of ['desktopWindow', 'blur', 'soft']) query.delete(key);
    const video = document.querySelector('video');
    if (route.path.startsWith('/watch/')) {
      query.set('popup', '1');
      if (video) { query.set('t', String(video.currentTime)); query.set('rate', String(video.playbackRate)); query.set('paused', video.paused ? '1' : '0'); }
    }
    const opened = await desktopRequest('/windows', { path: `${route.path}${query.size ? `?${query}` : ''}` });
    window.dispatchEvent(new CustomEvent('momoyu:desktop-open', { detail: opened }));
  } catch (error) { ElMessage.warning(error.message); }
  finally { isOpening.value = false; }
};
const handleKey = (event) => {
  if (nativeToken && event.key === 'Escape') { isOpen.value = false; change({ opacity: 100, clickThrough: false, autoHide: false }); }
};
onMounted(() => {
  refreshWindow().catch(() => {});
  if (nativeToken) timer = setInterval(() => refreshWindow().catch(() => {}), 800);
  document.addEventListener('keydown', handleKey);
});
onBeforeUnmount(() => { clearInterval(timer); clearTimeout(opacityTimer); document.removeEventListener('keydown', handleKey); });
</script>
<style scoped>
.display-dock { position: fixed; z-index: 50; left: max(.7rem, env(safe-area-inset-left)); bottom: max(.7rem, env(safe-area-inset-bottom)); display: flex; align-items: center; gap: .35rem; max-width: calc(100vw - 1.4rem); padding: .25rem; border: 1px solid var(--line); border-radius: 2rem; background: var(--panel); box-shadow: 0 .4rem 2rem #0005; }
.display-toggle, .settings-toggle { display: inline-flex; align-items: center; justify-content: center; gap: .45rem; min-height: 2.3rem; padding: .4rem .65rem; border-radius: 2rem; color: var(--muted); font-size: .72rem; white-space: nowrap; }
.display-toggle:hover, .settings-toggle:hover { color: var(--accent); background: #d2f86b0c; }
.opacity-level { display: flex; align-items: center; gap: .3rem; color: var(--muted); font-size: .65rem; }
.opacity-level input { width: clamp(3rem, 14vw, 5rem); accent-color: var(--accent); cursor: pointer; }
.opacity-level output { min-width: 2.1rem; font-variant-numeric: tabular-nums; }
.window-options { position: absolute; bottom: calc(100% + .5rem); left: 0; width: 11.5rem; padding: .5rem; background: var(--panel); border: 1px solid var(--line); border-radius: .8rem; box-shadow: 0 .4rem 2rem #0005; }
.window-options button { width: 100%; padding: .55rem .6rem; display: flex; align-items: center; gap: .6rem; font-size: .75rem; border-radius: .5rem; color: var(--muted); }
.window-options button:hover, .window-options button[aria-pressed="true"] { color: var(--accent); background: #d2f86b0c; }
.restore-hint { display: block; padding: .6rem .5rem .25rem; color: var(--subtle); font-size: .65rem; border-top: 1px solid var(--line); margin-top: .3rem; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@media (max-width: 500px) { .display-toggle { min-height: 2rem; font-size: .65rem; } .display-dock .utility-icon { width: .95rem; height: .95rem; } }
</style>
