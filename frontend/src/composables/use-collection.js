import { ref } from 'vue';

const readStored = (key) => {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '[]');
    return Array.isArray(value) ? value : [];
  } catch { return []; }
};

const savedIds = ref(readStored('pianke-saved').filter((id) => typeof id === 'string'));
const history = ref(readStored('pianke-history').filter((entry) => entry && typeof entry.id === 'string'));

const persist = (key, value) => {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* Browsing remains available if storage is full. */ }
};

export const useCollection = () => {
  const toggleSaved = (id) => {
    savedIds.value = savedIds.value.includes(id) ? savedIds.value.filter((item) => item !== id) : [...savedIds.value, id];
    persist('pianke-saved', savedIds.value);
  };
  const saveProgress = (id, episode, time, duration) => {
    const entry = { id, episode, time, duration, updatedAt: Date.now() };
    history.value = [entry, ...history.value.filter((item) => item.id !== id)].slice(0, 60);
    persist('pianke-history', history.value);
  };
  const clearHistory = () => {
    history.value = [];
    persist('pianke-history', []);
  };
  return { savedIds, history, toggleSaved, saveProgress, clearHistory };
};
