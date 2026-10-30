import { createApp } from 'vue';
import { createRouter, createWebHistory } from 'vue-router';
import App from './App.vue';

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

createApp(App).use(router).mount('#app');
