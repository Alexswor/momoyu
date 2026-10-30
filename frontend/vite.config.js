import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } } },
  build: { rollupOptions: { output: { manualChunks(id) { if (id.includes('/node_modules/hls.js/')) return 'player'; } } } },
});
