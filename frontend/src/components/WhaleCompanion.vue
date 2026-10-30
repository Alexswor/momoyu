<template>
  <aside v-if="isVisible && !route.path.startsWith('/watch/')" class="whale-companion" aria-label="鲸鱼娘快捷操作">
    <div v-if="isOpen" class="whale-menu"><button :disabled="isChoosing" @click="chooseDrama"><AppIcon name="sparkle" />{{ isChoosing ? '选片中…' : '摸鱼一下' }}</button><button @click="backToTop"><AppIcon name="up" />回到顶部</button><button @click="isVisible = false"><AppIcon name="close" />收起挂件</button></div>
    <button class="whale-toggle" aria-label="鲸鱼娘快捷操作" :aria-expanded="isOpen" @click="isOpen = !isOpen"><span class="whale-orbit" aria-hidden="true"></span><img :src="isOpen ? '/whale/icons/guide.webp' : '/whale/dafeiyu-chibi.png'" alt="Q版鲸鱼娘" /><span class="whale-spark" aria-hidden="true">✦</span></button>
  </aside>
</template>
<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus/es/components/message/index';
import AppIcon from './AppIcon.vue';
import { getDramas } from '../api/dramas';
const route = useRoute();
const router = useRouter();
const isVisible = ref(true);
const isOpen = ref(false);
const isChoosing = ref(false);
const backToTop = () => { window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' }); isOpen.value = false; };
const chooseDrama = async () => {
  isChoosing.value = true;
  try {
    const dramas = await getDramas({ source: String(route.query.source || 'zy360') });
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
.whale-toggle { width: 7rem; height: 8.5rem; padding: .1rem; position: relative; display: grid; place-items: center; }
.whale-toggle img { width: 7rem; height: 8.5rem; object-fit: contain; position: relative; filter: drop-shadow(0 4px 10px #0007); transform-origin: bottom; transition: transform .35s; }
.whale-toggle:hover img { transform: translateY(-4px) rotate(-5deg); }
.whale-orbit { position: absolute; bottom: 0; width: 6rem; height: 1rem; border-radius: 50%; background: #191d14e8; border: 1px solid #d2f86b30; box-shadow: 0 5px 28px #0005; }
.whale-spark { position: absolute; color: var(--accent); top: .2rem; right: .2rem; font-size: .9rem; }
.whale-menu { position: absolute; bottom: 9rem; right: 0; border: 1px solid var(--line); background: #1b1d18f5; box-shadow: 0 10px 40px #0005; padding: .4rem; border-radius: .8rem; width: 9.3rem; backdrop-filter: blur(12px); }
.whale-menu button { display: flex; gap: .6rem; align-items: center; width: 100%; padding: .75rem .65rem; font-size: .75rem; border-radius: .5rem; white-space: nowrap; }
.whale-menu button:hover { color: var(--accent); background: #d2f86b0c; }
.whale-menu .whale-icon { width: 1.8rem; height: 1.8rem; }
@media (max-width: 700px) { .whale-companion { right: .65rem; bottom: .8rem; } .whale-toggle { width: 4.6rem; height: 5.8rem; } .whale-toggle img { width: 4.6rem; height: 5.8rem; } .whale-orbit { width: 4rem; height: .65rem; } .whale-menu { bottom: 6.2rem; } }
</style>
