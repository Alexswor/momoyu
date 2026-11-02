import { test, expect } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';

let media;
test.setTimeout(45000);
const dramas = Array.from({ length: 24 }, (_, index) => ({
  id: `bf-${9001 + index}`, title: index ? `海风奇遇${index}` : '长夜里的鲸鱼来信：海风吹过第二季', category: '漫剧',
  poster: '/whale/dafeiyu-chibi.png', backdrop: '/whale/dafeiyu-chibi.png', tags: ['漫剧', '国语'],
  episodeCount: 2, originalEpisodeCount: 2, duration: 5, year: 2026, status: '全2集', sourceName: '测试片源',
  licenseName: 'CC0', credit: '测试素材', sourcePage: 'https://example.com', synopsis: '海风与鲸鱼的故事。',
}));

test.beforeAll(async ({ request }) => {
  // MDN's CC0 clip provides real seekable media without relying on a drama provider.
  const cache = new URL('../../.runtime/moyu-test-video.mp4', import.meta.url);
  if (existsSync(cache)) media = readFileSync(cache);
  else {
    const response = await request.get('https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4');
    expect(response.ok()).toBeTruthy();
    media = await response.body();
  }
});
test.beforeEach(async ({ context }) => {
  await context.route('**/moyu-test-video.mp4', (route) => {
    const range = /bytes=(\d+)-(\d*)/.exec(route.request().headers().range || '');
    const start = range ? Number(range[1]) : 0;
    const end = range?.[2] ? Number(range[2]) : media.length - 1;
    return route.fulfill({ status: range ? 206 : 200, contentType: 'video/mp4', body: media.subarray(start, end + 1), headers: {
      'Accept-Ranges': 'bytes', ...(range ? { 'Content-Range': `bytes ${start}-${end}/${media.length}` } : {}),
    } });
  });
  await context.route('http://127.0.0.1:5173/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    let data = dramas;
    if (path === '/api/catalog') data = { items: dramas, page: 1, pageCount: 1, total: dramas.length };
    else if (/\/play$/.test(path)) data = { dramaId: path.split('/')[3], episode: Number(path.split('/')[5]), urls: ['/moyu-test-video.mp4'], labels: ['原画'] };
    else if (/\/episodes$/.test(path)) data = [1, 2].map((number) => ({ number, title: `第${number}集 海风里的鲸鱼`, duration: 5 }));
    else if (/\/dramas\/[^/]+$/.test(path)) data = dramas.find((row) => row.id === path.split('/').pop());
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) });
  });
});

const readyVideo = async (page) => {
  await page.waitForFunction(() => document.querySelector('video')?.readyState >= 2);
  await page.locator('video').evaluate((video) => video.pause());
};

const desktopToken = '01234567-1234-1234-1234-0123456789ab';
test('半透明调节控制Windows窗口，页面内容保持清晰且Esc可恢复', async ({ page }) => {
  let state = { token: desktopToken, ready: true, opacity: 55, topmost: true, clickThrough: false, autoHide: false, restoreKey: 'Alt+B' };
  await page.route('**/api/desktop/windows/**', async (route) => {
    if (route.request().method() === 'POST') state = { ...state, ...route.request().postDataJSON() };
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: state }) });
  });
  await page.goto(`/?desktopWindow=${desktopToken}`);
  await expect(page.getByRole('slider', { name: '窗口不透明度' })).toHaveValue('55');
  await page.getByRole('slider', { name: '窗口不透明度' }).fill('35');
  await expect.poll(() => state.opacity).toBe(35);
  expect(await page.locator('main').evaluate(el => getComputedStyle(el).filter)).toBe('none');
  await page.getByRole('button', { name: '小窗设置' }).click();
  await page.getByRole('button', { name: '鼠标穿透', exact: true }).click();
  await expect.poll(() => state.clickThrough).toBe(true);
  await page.keyboard.press('Escape');
  await expect.poll(() => state.opacity).toBe(100);
  expect(state.clickThrough).toBe(false);
  await page.getByRole('link', { name: '全部漫剧', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`desktopWindow=${desktopToken}`));
});

test('弹窗交接真实播放进度、倍速和暂停状态，返回后继续同一位置', async ({ page }) => {
  await page.goto('/watch/bf-9001');
  await readyVideo(page);
  await page.locator('video').evaluate((video) => { video.currentTime = 1.2; });
  await page.locator('.speed-select').click();
  await page.getByRole('option', { name: '1.5倍速', exact: true }).click();
  const opened = page.waitForEvent('popup');
  await page.getByRole('button', { name: '弹窗播放', exact: true }).click();
  const popup = await opened;
  await readyVideo(popup);
  await expect(page.getByText('正在弹窗播放', { exact: true })).toBeVisible();
  await expect(popup.locator('.site-header')).toHaveCount(0);
  expect(await popup.locator('video').evaluate((video) => video.currentTime)).toBeGreaterThan(1);
  expect(await popup.locator('video').evaluate((video) => video.playbackRate)).toBe(1.5);
  await popup.setViewportSize({ width: 400, height: 600 });
  await popup.getByRole('button', { name: '选集', exact: true }).click();
  await expect(popup.getByRole('complementary', { name: '选集', exact: true })).toBeVisible();
  await popup.getByRole('button', { name: '选集', exact: true }).click();
  for (const width of [320, 400, 640]) {
    await popup.setViewportSize({ width, height: 600 });
    expect(await popup.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy();
  }
  await popup.setViewportSize({ width: 400, height: 600 });
  await popup.screenshot({ path: '../.runtime/moyu-popup.png' });
  await popup.locator('video').evaluate((video) => { video.currentTime = 2.5; });
  const closed = popup.waitForEvent('close');
  await popup.getByRole('button', { name: '回到网页', exact: true }).click();
  await closed;
  await expect.poll(() => page.locator('video').evaluate((video) => video.currentTime)).toBeGreaterThan(2.3);
  await expect(page.locator('.detached-overlay')).toHaveCount(0);
  expect(await page.locator('video').evaluate((video) => video.paused)).toBeTruthy();
});

test('弹窗选下一集后仍保持精简布局，关闭窗口也回传新集数', async ({ page }) => {
  await page.goto('/watch/bf-9001');
  await readyVideo(page);
  const opened = page.waitForEvent('popup');
  await page.getByRole('button', { name: '弹窗播放', exact: true }).click();
  const popup = await opened;
  await readyVideo(popup);
  await popup.getByRole('button', { name: '下一集', exact: true }).click();
  await expect(popup).toHaveURL(/\/watch\/bf-9001\/2\?popup=1/);
  await readyVideo(popup);
  await popup.locator('video').evaluate((video) => { video.currentTime = 2; });
  await popup.close({ runBeforeUnload: true });
  await expect(page).toHaveURL(/\/watch\/bf-9001\/2/);
  await expect.poll(() => page.locator('video').evaluate((video) => video.currentTime)).toBeGreaterThan(1.8);
});

test('拦截弹窗时保留网页播放并显示可恢复的提示', async ({ page }) => {
  await page.goto('/watch/bf-9001');
  await readyVideo(page);
  await page.evaluate(() => { window.open = () => null; });
  await page.locator('video').evaluate(async (video) => { video.muted = true; await video.play(); });
  await page.getByRole('button', { name: '弹窗播放', exact: true }).click();
  await expect(page.getByText('弹窗被拦截，请允许本站弹出窗口后重试', { exact: true })).toBeVisible();
  await expect(page.locator('.detached-overlay')).toHaveCount(0);
  expect(await page.locator('video').evaluate((video) => video.paused)).toBeFalsy();
});

test('首页、片库、观看页在窄屏和半屏窗口内均无横向溢出', async ({ page }) => {
  for (const path of ['/', '/library', '/watch/bf-9001']) {
    await page.goto(path);
    await expect(page.locator('.drama-card, video').first()).toBeVisible();
    for (const width of [320, 390, 560, 702, 768, 900, 1440]) {
      await page.setViewportSize({ width, height: 820 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `${path} at ${width}px`).toBeTruthy();
      if (path.startsWith('/watch/') && width <= 1000) {
        const player = await page.locator('.player-section').boundingBox();
        const episodes = await page.locator('.episodes-panel').boundingBox();
        expect(episodes.y, `选集应排在播放器下方：${width}px`).toBeGreaterThanOrEqual(player.y + player.height);
      }
    }
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: '../.runtime/moyu-watch-mobile.png', fullPage: true });
});

test('网页启动半透明小窗携带集数和进度，关闭后回传新进度', async ({ page }) => {
  let openedPath;
  let state = { token: desktopToken, ready: true, closed: false, progress: null };
  await page.route('**/api/desktop**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/windows') && route.request().method() === 'POST') openedPath = route.request().postDataJSON().path;
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: path.endsWith('/desktop') ? { available: true } : state }) });
  });
  await page.goto('/watch/bf-9001');
  await readyVideo(page);
  await page.locator('video').evaluate(video => { video.currentTime = 1.2; });
  await page.getByRole('button', { name: '半透明小窗', exact: true }).click();
  expect(openedPath).toContain('/watch/bf-9001?');
  expect(new URL(openedPath, 'http://local').searchParams.get('t')).toBe('1.2');
  await expect(page.getByText('正在弹窗播放', { exact: true })).toBeVisible();
  state = { ...state, closed: true, progress: { id: 'bf-9001', episode: 1, time: 2.5, rate: 1.5, resume: false } };
  await expect.poll(() => page.locator('video').evaluate(video => video.currentTime)).toBeGreaterThan(2.3);
  await expect(page.locator('.detached-overlay')).toHaveCount(0);
});
