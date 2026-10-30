import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';
import { normalizeSource } from './constants/catalog-sources';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./pages/DiscoverPage.vue') },
    { path: '/library', component: () => import('./pages/DiscoverPage.vue') },
    { path: '/saved', component: () => import('./pages/DiscoverPage.vue') },
    { path: '/history', component: () => import('./pages/DiscoverPage.vue') },
    { path: '/watch/:id/:episode?', component: () => import('./pages/WatchPage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach((to) => {
  if (to.query.source !== undefined && normalizeSource(to.query.source) !== to.query.source) {
    return { path: to.path, query: { ...to.query, source: normalizeSource(to.query.source) }, hash: to.hash, replace: true };
  }
});

createApp(App).use(router).mount('#app');
