<template>
  <aside v-if="isVisible && !route.path.startsWith('/watch/')" ref="companionElement" class="whale-companion" :class="{ 'is-positioned': position, 'is-dragging': isDragging }" aria-label="鲸鱼娘快捷操作">
    <div v-if="isOpen" class="whale-menu" :class="{ 'opens-below': opensBelow, 'aligns-left': alignsLeft }"><button :disabled="isChoosing" @click="chooseDrama"><WhaleMenuIcon action="pick" />{{ isChoosing ? '选片中…' : '摸鱼一下' }}</button><button @click="backToTop"><WhaleMenuIcon action="top" />回到顶部</button><button @click="isVisible = false"><WhaleMenuIcon action="hide" />收起挂件</button></div>
    <button class="whale-toggle" aria-label="鲸鱼娘快捷操作" title="拖动调整位置，点击打开菜单" :aria-expanded="isOpen" @pointerdown="startDrag" @pointermove="moveDrag" @pointerup="endDrag" @pointercancel="cancelDrag" @lostpointercapture="cancelDrag" @click="toggleMenu" @keydown="moveWithKeyboard"><span class="whale-orbit" aria-hidden="true"></span><img :src="isOpen ? '/whale/icons/guide.webp' : '/whale/dafeiyu-chibi.png'" alt="Q版鲸鱼娘" draggable="false" @dragstart.prevent /><span class="whale-spark" aria-hidden="true">✦</span></button>
  </aside>
</template>
<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index';
import WhaleMenuIcon from './WhaleMenuIcon.vue';
import { getDramas } from '../api/dramas';
import { normalizeSource } from '../constants/catalog-sources';
const route = useRoute();
const router = useRouter();
const isVisible = ref(true);
const isOpen = ref(false);
const isChoosing = ref(false);
const companionElement = ref(null);
const position = ref(null);
const isDragging = ref(false);
const leftCss = computed(() => `${position.value?.x || 0}px`);
const topCss = computed(() => `${position.value?.y || 0}px`);
const opensBelow = computed(() => position.value && position.value.y < 170);
const alignsLeft = computed(() => position.value && position.value.x < 180);
let gesture = null;
let didDrag = false;
const savePosition = () => {
  if (position.value) try { localStorage.setItem('momoyu-whale-position', JSON.stringify(position.value)); } catch { /* Position memory is optional. */ }
};
const place = (x, y) => {
  const element = companionElement.value;
  if (!element) return;
  position.value = {
    x: Math.max(8, Math.min(x, window.innerWidth - element.offsetWidth - 8)),
    y: Math.max(8, Math.min(y, window.innerHeight - element.offsetHeight - 8)),
  };
};
const fitViewport = () => { if (position.value) { place(position.value.x, position.value.y); savePosition(); } };
const startDrag = (event) => {
  if (event.button !== 0 || !event.isPrimary) return;
  event.preventDefault();
  event.currentTarget.focus({ preventScroll: true });
  const box = companionElement.value.getBoundingClientRect();
  gesture = { id: event.pointerId, x: event.clientX, y: event.clientY, left: box.left, top: box.top };
  didDrag = false;
  event.currentTarget.setPointerCapture(event.pointerId);
};
const moveDrag = (event) => {
  if (!gesture || gesture.id !== event.pointerId) return;
  const dx = event.clientX - gesture.x, dy = event.clientY - gesture.y;
  if (!didDrag && Math.hypot(dx, dy) < 5) return;
  didDrag = true;
  isDragging.value = true;
  isOpen.value = false;
  place(gesture.left + dx, gesture.top + dy);
};
const endDrag = (event) => {
  if (!gesture || gesture.id !== event.pointerId) return;
  gesture = null;
  isDragging.value = false;
  if (didDrag) savePosition();
  if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
};
const cancelDrag = () => {
  if (!gesture) return;
  gesture = null;
  isDragging.value = false;
  didDrag = false;
  savePosition();
};
const toggleMenu = (event) => {
  if (didDrag && event.detail !== 0) { didDrag = false; return; }
  isOpen.value = !isOpen.value;
};
const moveWithKeyboard = (event) => {
  const direction = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key];
  if (!direction) return;
  event.preventDefault();
  const box = companionElement.value.getBoundingClientRect();
  const step = event.shiftKey ? 4 : 16;
  place(box.left + direction[0] * step, box.top + direction[1] * step);
  savePosition();
};
onMounted(async () => {
  try {
    const saved = JSON.parse(localStorage.getItem('momoyu-whale-position') || 'null');
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) position.value = saved;
  } catch { /* Ignore invalid or unavailable position memory. */ }
  await nextTick();
  fitViewport();
  window.addEventListener('resize', fitViewport);
});
watch(() => route.path, () => nextTick(fitViewport));
onBeforeUnmount(() => window.removeEventListener('resize', fitViewport));
const backToTop = () => { window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' }); isOpen.value = false; };
const chooseDrama = async () => {
  isChoosing.value = true;
  try {
    const dramas = await getDramas({ source: normalizeSource(route.query.source) });
    if (!dramas.length) { ElMessage.info('暂时没有可选漫剧'); return; }
    const drama = dramas[Math.floor(Math.random() * dramas.length)];
    isOpen.value = false;
    router.push(`/watch/${drama.id}`);
  } catch (error) { ElMessage.error(error.message); }
  finally { isChoosing.value = false; }
};
</script>
<style scoped>
.whale-companion { position: fixed; right: 1.5rem; bottom: 1.2rem; z-index: 30; }
.whale-companion.is-positioned { left: v-bind(leftCss); top: v-bind(topCss); right: auto; bottom: auto; }
.whale-toggle { width: 7rem; height: 8.5rem; padding: .1rem; position: relative; display: grid; place-items: center; }
.whale-toggle { touch-action: none; user-select: none; cursor: grab; }
.whale-toggle > * { pointer-events: none; }
.is-dragging .whale-toggle { cursor: grabbing; }
.whale-toggle img { width: 7rem; height: 8.5rem; object-fit: contain; position: relative; filter: drop-shadow(0 4px 10px #0007); transform-origin: bottom; transition: transform .35s; }
.whale-toggle:hover img { transform: translateY(-4px) rotate(-5deg); }
.whale-orbit { position: absolute; bottom: 0; width: 6rem; height: 1rem; border-radius: 50%; background: #191d14e8; border: 1px solid #d2f86b30; box-shadow: 0 5px 28px #0005; }
.whale-spark { position: absolute; color: var(--accent); top: .2rem; right: .2rem; font-size: .9rem; }
.whale-menu { position: absolute; bottom: 9rem; right: 0; border: 1px solid var(--line); background: #1b1d18f5; box-shadow: 0 10px 40px #0005; padding: .4rem; border-radius: .8rem; width: 10.8rem; backdrop-filter: blur(12px); }
.whale-menu button { display: flex; gap: .7rem; align-items: center; width: 100%; padding: .5rem .65rem; font-size: .75rem; border-radius: .5rem; white-space: nowrap; }
.whale-menu button:hover { color: var(--accent); background: #d2f86b0c; }
.whale-menu.opens-below { top: calc(100% + .5rem); bottom: auto; }
.whale-menu.aligns-left { left: 0; right: auto; }
@media (max-width: 700px) { .whale-companion { right: .45rem; bottom: 3.6rem; } .whale-toggle { width: 3.3rem; height: 4.2rem; } .whale-toggle img { width: 3.3rem; height: 4.2rem; } .whale-orbit { width: 2.9rem; height: .5rem; } .whale-spark { font-size: .7rem; } .whale-menu { bottom: 4.5rem; width: 10rem; } .whale-menu button { gap: .5rem; padding: .4rem .5rem; font-size: .7rem; } }
</style>
