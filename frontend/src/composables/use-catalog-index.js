import { shallowReactive } from 'vue';
import { getCatalogPage } from '../api/dramas';

const indexes = new Map();
const CACHE_TTL = 5 * 60 * 1000;
const PAGE_SIZE = 100;
const WORKERS = 2;

// The complete text index is fetched automatically. Images and cards remain lazy in the view.
export const getCatalogIndex = (source, keyword = '', refresh = false) => {
  const key = `${source}:${keyword}`;
  const previous = indexes.get(key);
  if (!refresh && previous && Date.now() - previous.startedAt < CACHE_TTL) return previous;
  const state = shallowReactive({ items: [], isComplete: false, isPrefetching: true, error: '', loadedPages: 0, pageCount: 0, total: 0, startedAt: Date.now(), ready: null });
  if (indexes.size >= 6) indexes.delete(indexes.keys().next().value);
  indexes.set(key, state);
  const pages = new Map();
  const mergePage = (data) => {
    pages.set(data.page, data.items);
    const seen = new Set();
    state.items = Array.from(pages.entries()).sort(([a], [b]) => a - b).flatMap(([, items]) => items).filter((item) => {
      if (seen.has(item.id)) return false;
      seen.add(item.id);
      return true;
    });
    state.loadedPages = pages.size;
  };
  state.ready = (async () => {
    try {
      const first = await getCatalogPage({ source, keyword, page: '1', size: String(PAGE_SIZE) });
      state.pageCount = first.pageCount;
      state.total = first.total;
      mergePage(first);
      let nextPage = 2;
      const runWorker = async () => {
        while (nextPage <= state.pageCount) {
          const page = nextPage++;
          let data;
          for (let attempt = 0; attempt < 2; attempt++) {
            try { data = await getCatalogPage({ source, keyword, page: String(page), size: String(PAGE_SIZE) }); break; }
            catch (error) { if (attempt === 1) throw error; }
          }
          mergePage(data);
        }
      };
      // Continue after the first page is usable; callers never wait for the complete index to see content.
      Promise.allSettled(Array.from({ length: WORKERS }, runWorker)).then((results) => {
        const failure = results.find((result) => result.status === 'rejected');
        state.isPrefetching = false;
        state.isComplete = !failure && state.loadedPages >= state.pageCount;
        state.error = failure ? '片库暂时未完全载入' : '';
      });
    } catch (error) {
      state.isPrefetching = false;
      state.error = error.message;
      throw error;
    }
  })();
  return state;
};
