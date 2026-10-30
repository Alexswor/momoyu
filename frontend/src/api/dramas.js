const API_BASE = import.meta.env.VITE_API_BASE || '/api';

const request = async (path, signal) => {
  const response = await fetch(`${API_BASE}${path}`, { signal });
  const result = await response.json();
  if (!response.ok || result.code !== 200) throw new Error(result.message || '加载失败，请稍后重试');
  return result.data;
};

export const getDramas = (params = {}, signal) => request(`/dramas?${new URLSearchParams(params)}`, signal);
export const getCatalogPage = (params = {}) => request(`/catalog?${new URLSearchParams(params)}`);
export const getDrama = (id) => request(`/dramas/${encodeURIComponent(id)}`);
export const getEpisodes = (id) => request(`/dramas/${encodeURIComponent(id)}/episodes`);
export const getPlayback = (id, number) => request(`/dramas/${encodeURIComponent(id)}/episodes/${number}/play`);
