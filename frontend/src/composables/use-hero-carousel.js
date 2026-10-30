import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const SLIDE_DURATION = 6000;

export function useHeroCarousel(index, count, enabled) {
  const isPaused = ref(false);
  const isHovered = ref(false);
  const hasFocus = ref(false);
  const isHidden = ref(false);
  const reducedMotion = ref(false);
  let timer;
  let mediaQuery;

  const isPlaying = computed(() => enabled.value && count.value > 1
    && !isPaused.value && !isHovered.value && !hasFocus.value
    && !isHidden.value && !reducedMotion.value);
  const clearTimer = () => { window.clearTimeout(timer); };
  const schedule = () => {
    clearTimer();
    if (isPlaying.value) timer = window.setTimeout(() => {
      index.value = (index.value + 1) % count.value;
    }, SLIDE_DURATION);
  };
  const selectSlide = (nextIndex) => {
    if (!count.value) return;
    index.value = (nextIndex + count.value) % count.value;
    schedule();
  };
  const changeSlide = (direction) => selectSlide(index.value + direction);
  const onFocusOut = (event) => {
    hasFocus.value = event.currentTarget.contains(event.relatedTarget);
  };
  const syncVisibility = () => { isHidden.value = document.hidden; };
  const syncMotion = () => { reducedMotion.value = mediaQuery.matches; };

  watch([isPlaying, index, count], schedule);
  onMounted(() => {
    mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    syncVisibility();
    syncMotion();
    document.addEventListener('visibilitychange', syncVisibility);
    mediaQuery.addEventListener('change', syncMotion);
    schedule();
  });
  onBeforeUnmount(() => {
    clearTimer();
    document.removeEventListener('visibilitychange', syncVisibility);
    mediaQuery?.removeEventListener('change', syncMotion);
  });

  return { isPaused, isHovered, hasFocus, isPlaying, reducedMotion, selectSlide, changeSlide, onFocusOut };
}
