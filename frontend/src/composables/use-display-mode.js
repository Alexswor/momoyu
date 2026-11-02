import { ref } from 'vue';
const candidate = new URLSearchParams(location.search).get('desktopWindow') || '';
export const nativeToken = /^[a-f0-9-]{36}$/.test(candidate) ? candidate : '';
export const desktopRequest = async (path = '', body, keepalive = false) => {
  const response = await fetch(`${import.meta.env.VITE_API_BASE || '/api'}/desktop${path}`, {
    ...(body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }), keepalive,
  });
  const result = await response.json();
  if (!response.ok || result.code !== 200) throw new Error(result.message || '窗口连接失败，请重新打开');
  return result.data;
};
const isAvailable = ref(false);
const windowState = ref({ opacity: 100, topmost: false, clickThrough: false, autoHide: false, ready: false, restoreKey: '' });
const refreshWindow = async () => {
  if (nativeToken) {
    windowState.value = await desktopRequest(`/windows/${nativeToken}`);
    const video = document.querySelector('video');
    await desktopRequest(`/windows/${nativeToken}/status`, {
      readyState: video?.readyState ?? -1, networkState: video?.networkState ?? 0, time: video?.currentTime || 0,
      error: String(video?.dataset.playerError || video?.error?.message || document.querySelector('[role="alert"]')?.textContent || '').slice(0, 500),
    });
  } else isAvailable.value = Boolean((await desktopRequest()).available);
};
const updateWindow = async (changes) => {
  const { opacity, topmost, clickThrough, autoHide } = windowState.value;
  windowState.value = await desktopRequest(`/windows/${nativeToken}`, { opacity, topmost, clickThrough, autoHide, ...changes });
};
export const useDisplayMode = () => ({ nativeToken, isAvailable, windowState, refreshWindow, updateWindow });
