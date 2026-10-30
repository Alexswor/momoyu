import { test, expect } from '@playwright/test';

test('实时漫剧目录、搜索、分页和片单持久化', async ({ page, request }) => {
  const catalog = (await (await request.get('/api/dramas')).json()).data;
  expect(catalog.length).toBeGreaterThanOrEqual(6);
  const drama = catalog[0];
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: drama.title, exact: true })).toBeVisible();
  await expect(page.locator('.drama-card')).toHaveCount(6);
  await expect(page.getByText('没有找到相关作品', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: `切换至${catalog[1].title}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: catalog[1].title, exact: true })).toBeVisible();
  await page.getByRole('link', { name: '全部漫剧', exact: true }).click();
  await expect.poll(() => page.locator('.drama-card').count()).toBeGreaterThanOrEqual(24);
  await page.getByRole('button', { name: `收藏${drama.title}`, exact: true }).click();
  await page.getByRole('link', { name: '我的片单', exact: true }).click();
  await expect(page.locator('.drama-card')).toHaveCount(1);
  await page.reload();
  await expect(page.locator('.drama-card')).toHaveCount(1);
  await page.getByRole('button', { name: `取消收藏${drama.title}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: '片单还空着' })).toBeVisible();
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).fill(drama.title);
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).press('Enter');
  await expect(page.locator('.drama-card')).not.toHaveCount(0);
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).fill('zxqnosuchtitle9918384');
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).press('Enter');
  await expect(page.getByRole('heading', { name: '没有找到相关作品' })).toBeVisible();
  await page.getByRole('link', { name: '全部漫剧', exact: true }).click();
  await expect.poll(() => page.locator('.drama-card').count()).toBeGreaterThanOrEqual(24);
  await expect(page.getByRole('button', { name: '加载更多', exact: true })).toHaveCount(0);
  const initialCards = await page.locator('.drama-card').count();
  await page.locator('.lazy-sentinel').scrollIntoViewIfNeeded();
  await expect.poll(() => page.locator('.drama-card').count(), { timeout: 25000 }).toBeGreaterThan(initialCards);
  expect(errors).toEqual([]);
});

test('暴风真实HLS全集播放、拖动、倍速、断点续播和观看记录', async ({ page, request }) => {
  const drama = (await (await request.get('/api/dramas/bf-170347')).json()).data;
  await page.goto('/watch/bf-170347');
  const video = page.locator('video');
  await expect(video).toBeVisible();
  await page.waitForFunction(() => document.querySelector('video')?.readyState >= 2, null, { timeout: 45000 });
  expect(await video.evaluate((element) => element.duration)).toBeGreaterThan(7200);
  await expect(page.getByText(`全集版 · ${drama.originalEpisodeCount}集`, { exact: true })).toBeVisible();
  await video.evaluate(async (element) => { element.muted = true; await element.play(); });
  await expect.poll(() => video.evaluate((element) => element.currentTime), { timeout: 20000 }).toBeGreaterThan(2);
  await video.evaluate((element) => { element.currentTime = 45; });
  await page.waitForFunction(() => document.querySelector('video')?.currentTime >= 45 && !document.querySelector('video').seeking, null, { timeout: 30000 });
  await page.locator('.speed-select').click();
  await page.getByRole('option', { name: '1.5倍速', exact: true }).click();
  await expect.poll(() => video.evaluate((element) => element.playbackRate)).toBe(1.5);
  await video.evaluate((element) => element.pause());
  await page.getByRole('link', { name: '观看记录', exact: true }).click();
  await expect(page.locator('.drama-card')).toHaveCount(1);
  await page.getByRole('link', { name: `播放${drama.title}`, exact: true }).click();
  await page.waitForFunction(() => document.querySelector('video')?.readyState >= 2 && document.querySelector('video').currentTime > 40, null, { timeout: 45000 });
  await expect(page.getByRole('button', { name: '下一集', exact: true })).toBeDisabled();
});

test('手机布局、字体和竖屏观看', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.locator('.drama-card')).toHaveCount(6);
  await page.evaluate(() => document.fonts.ready);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  expect(await page.evaluate(() => document.fonts.check('16px "Chill Round"'))).toBeTruthy();
  await page.screenshot({ path: '../.runtime/ai-home-mobile.png', fullPage: true });
  await page.goto('/watch/zy360-108805');
  await page.waitForFunction(() => document.querySelector('video')?.readyState >= 2, null, { timeout: 45000 });
  await expect(page.locator('.video-stage')).toHaveClass(/is-portrait/);
  const box = await page.locator('.video-stage').boundingBox();
  expect(box.height).toBeGreaterThan(box.width);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../.runtime/ai-watch-mobile.png', fullPage: true });
});

test('播放请求故障时可重试', async ({ page }) => {
  await page.route('**/api/dramas/zy360-108805/episodes/1/play', (route) => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '片源暂时不可用', data: null }) }));
  await page.goto('/watch/zy360-108805');
  await expect(page.getByRole('alert')).toContainText('片源暂时不可用');
  await expect(page.getByRole('button', { name: '重新播放', exact: true })).toBeVisible();
});

test('封面通过本站缓存加载，保留影院配色并显示鲸鱼娘', async ({ page, request }) => {
  await page.route('**://www.imgzy360.com:7788/**', (route) => route.abort());
  const response = await request.get('/api/dramas/bf-170347/poster');
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toContain('image/');
  expect(response.headers()['cache-control']).toContain('max-age=3600');
  await page.goto('/');
  await expect(page).toHaveTitle('摸摸鱼 · 漫剧');
  await expect(page.getByRole('link', { name: '摸摸鱼首页' })).toBeVisible();
  await expect(page.locator('.drama-card')).toHaveCount(6);
  await page.waitForFunction(() => Array.from(document.querySelectorAll('.poster-link img')).every((image) => image.complete && image.naturalWidth > 0 && !image.dataset.fallback), null, { timeout: 30000 });
  expect(await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--accent').trim())).toBe('#d2f86b');
  await page.getByRole('button', { name: '鲸鱼娘快捷操作', exact: true }).click();
  await expect(page.getByRole('button', { name: '摸鱼一下', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '收起挂件', exact: true }).click();
  await expect(page.locator('.whale-companion')).toHaveCount(0);
  expect(await page.locator('link[rel="icon"]').getAttribute('href')).toBe('/whale/dafeiyu-chibi.png');
});

test('片库元数据自动完整载入，图片与卡片保持懒加载', async ({ page }) => {
  // 暴风接口每页最多100条，完整目录约161页，保留两路并发以限制上游请求压力。
  test.setTimeout(420000);
  await page.goto('/library');
  await expect.poll(() => page.locator('.drama-card').count()).toBeGreaterThanOrEqual(24);
  await expect(page.getByRole('status', { name: '正在载入完整片库' })).toHaveCount(0, { timeout: 390000 });
  await expect(page.locator('.catalog-error')).toHaveCount(0);
  const total = Number((await page.locator('.count-label').innerText()).replace(/\D/g, ''));
  expect(total).toBeGreaterThan(16000);
  const rendered = await page.locator('.drama-card').count();
  expect(rendered).toBeLessThan(total);
  await expect(page.getByRole('button', { name: '加载更多' })).toHaveCount(0);
  await test.info().attach('catalog-summary', { body: JSON.stringify({ total, rendered }), contentType: 'application/json' });
  await page.screenshot({ path: '../.runtime/baofeng-complete-library.png' });
});
