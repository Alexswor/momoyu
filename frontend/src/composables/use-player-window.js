import { computed, onMounted, onBeforeUnmount } from 'vue';
import { ElMessage } from 'element-plus/es/components/message/index';
import { nativeToken, desktopRequest } from './use-display-mode';

export const usePlayerWindow = ({ route, router, videoElement, drama, episodeNumber, playbackRate, isPortrait, isPlaybackDetached, history, persistProgress }) => {
  const isPopup = computed(() => route.query.popup === '1');
  let playerWindow = null;
  let hasReturned = false;
  let desktopWindow = null;
  let closedAt = 0;
  let isPollingDesktop = false;

  const openPlayerWindow = () => {
    if (isPlaybackDetached.value && playerWindow && !playerWindow.closed) { playerWindow.focus(); return; }
    const video = videoElement.value;
    if (!video || !drama.value) return;
    const width = Math.min(isPortrait.value ? 400 : 640, screen.availWidth);
    const height = Math.min(isPortrait.value ? 780 : 520, screen.availHeight);
    const url = router.resolve({ path: `/watch/${drama.value.id}/${episodeNumber.value}`, query: {
      popup: '1', t: String(Math.max(0, video.currentTime)), rate: String(playbackRate.value),
      paused: video.paused ? '1' : '0',
    } }).href;
    persistProgress();
    playerWindow = window.open(url, 'momoyu-player', `popup=yes,width=${width},height=${height},resizable=yes,scrollbars=yes`);
    if (!playerWindow) { isPlaybackDetached.value = false; ElMessage.warning('弹窗被拦截，请允许本站弹出窗口后重试'); return; }
    isPlaybackDetached.value = true;
    video.pause();
    playerWindow.focus();
  };

  const sendProgress = (resume = false) => {
    const video = videoElement.value;
    if (!video || !drama.value || !window.opener || window.opener.closed) return false;
    persistProgress();
    window.opener.postMessage({ type: 'momoyu:player-return', id: drama.value.id, episode: episodeNumber.value,
      time: video.currentTime, rate: playbackRate.value, resume }, location.origin);
    return true;
  };
  const returnToPage = async () => {
    const video = videoElement.value;
    if (nativeToken) {
      try {
        if (video && drama.value) await desktopRequest(`/windows/${nativeToken}/progress`, { id: drama.value.id, episode: episodeNumber.value, time: video.currentTime, rate: playbackRate.value, resume: !video.paused });
        hasReturned = true;
        await desktopRequest(`/windows/${nativeToken}/close`, {});
      } catch (error) { ElMessage.warning(error.message); }
      return;
    }
    if (sendProgress(Boolean(video && !video.paused))) {
      hasReturned = true;
      window.opener.focus();
      window.close();
      return;
    }
    const query = { ...route.query };
    delete query.popup;
    router.replace({ path: route.path, query });
  };
  const handlePageHide = () => { if (nativeToken && !hasReturned) persistProgress(); else if (isPopup.value && !hasReturned) sendProgress(); };
  const applyProgress = (data) => {
    isPlaybackDetached.value = false;
    if (data.id !== drama.value?.id) return;
    if (!Number.isInteger(data.episode) || data.episode < 1 || !Number.isFinite(data.time) || data.time < 0 || ![0.75, 1, 1.25, 1.5, 2].includes(data.rate)) return;
    playbackRate.value = data.rate;
    if (data.episode !== episodeNumber.value) {
      router.push({ path: `/watch/${data.id}/${data.episode}`, query: { t: String(data.time), paused: data.resume ? '0' : '1' } });
      return;
    }
    const video = videoElement.value;
    if (video?.readyState >= 1 && Number.isFinite(video.duration)) {
      video.currentTime = Math.min(data.time, Math.max(0, video.duration - 1));
      video.playbackRate = data.rate;
      if (data.resume) video.play().catch(() => {});
    }
  };
  const handleReturn = (event) => {
    if (event.origin !== location.origin) return;
    if (isPopup.value && event.source === window.opener && event.data?.type === 'momoyu:recall') { returnToPage(); return; }
    if (!playerWindow || event.source !== playerWindow || event.data?.type !== 'momoyu:player-return') return;
    applyProgress(event.data);
  };
  const recallPlayer = () => {
    if (desktopWindow) { desktopRequest(`/windows/${desktopWindow}/close`, {}).catch((error) => ElMessage.warning(error.message)); return; }
    if (playerWindow && !playerWindow.closed) playerWindow.postMessage({ type: 'momoyu:recall' }, location.origin);
    else {
      isPlaybackDetached.value = false;
      const previous = history.value.find((entry) => entry.id === drama.value?.id);
      if (previous) applyProgress({ ...previous, rate: playbackRate.value, resume: false });
    }
  };
  let closeTimer;
  const handleDesktopOpen = (event) => {
    if (!drama.value || !event.detail?.token) return;
    persistProgress();
    desktopWindow = event.detail.token;
    closedAt = 0;
    isPlaybackDetached.value = true;
    videoElement.value?.pause();
  };
  const checkDesktop = async () => {
    if (!desktopWindow || isPollingDesktop) return;
    isPollingDesktop = true;
    try {
      const state = await desktopRequest(`/windows/${desktopWindow}`);
      if (state.closed) {
        if (!closedAt) { closedAt = Date.now(); return; }
        if (Date.now() - closedAt < 400) return;
        desktopWindow = null;
        isPlaybackDetached.value = false;
        if (state.error) ElMessage.warning(state.error);
        if (state.progress) applyProgress(state.progress);
      }
    } catch { /* A brief backend restart should not interrupt the player. */ }
    finally { isPollingDesktop = false; }
  };
  onMounted(() => {
    window.addEventListener('message', handleReturn);
    window.addEventListener('pagehide', handlePageHide);
    window.addEventListener('momoyu:desktop-open', handleDesktopOpen);
    closeTimer = window.setInterval(() => { checkDesktop(); if (isPlaybackDetached.value && playerWindow?.closed && !desktopWindow) recallPlayer(); }, 800);
  });
  onBeforeUnmount(() => {
    handlePageHide();
    window.removeEventListener('message', handleReturn);
    window.removeEventListener('pagehide', handlePageHide);
    window.removeEventListener('momoyu:desktop-open', handleDesktopOpen);
    clearInterval(closeTimer);
  });
  return { isPopup, openPlayerWindow, returnToPage, recallPlayer };
};
