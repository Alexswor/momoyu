import { test, expect } from '@playwright/test';

const CATALOG_SIZE = 96;
const catalog = (source) => Array.from({ length: CATALOG_SIZE }, (_, index) => ({
  id: `${source}-${index + 1}`, title: `海风奇遇${index + 1}`, category: '漫剧', tags: ['漫剧'],
  poster: '/whale/dafeiyu-chibi.png', backdrop: '/whale/dafeiyu-chibi.png',
  compilation: true, originalEpisodeCount: 60, status: '完结', year: 2026,
}));

test('旧片源目录链接转到暴风，保留搜索词且不请求停用接口', async ({ page }) => {
  const requests = [];
  await page.route('**/api/catalog?**', (route) => {
    const url = new URL(route.request().url());
    requests.push({ source: url.searchParams.get('source'), keyword: url.searchParams.get('keyword') });
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { items: catalog('bf'), page: 1, pageCount: 1, total: CATALOG_SIZE } }) });
  });
  for (const source of ['wj', 'ff']) {
    await page.goto(`/?source=${source}&q=海风`);
    await expect(page.locator('.drama-card')).toHaveCount(6);
    expect(new URL(page.url()).searchParams.get('source')).toBe('bf');
    expect(new URL(page.url()).searchParams.get('q')).toBe('海风');
    await page.locator('.source-select').click();
    await expect(page.getByRole('option')).toHaveCount(2);
    await expect(page.getByRole('option', { name: '暴风资源', exact: true })).toBeVisible();
    await expect(page.getByRole('option', { name: '360资源', exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
  }
  expect(requests.length).toBeGreaterThanOrEqual(2);
  expect(requests.every(({ source, keyword }) => source === 'bf' && keyword === '海风')).toBeTruthy();
});

test('暴风目录故障可切换360，恢复目录后仍按滚动懒加载', async ({ page }) => {
  await page.route('**/api/catalog?**', (route) => {
    const source = new URL(route.request().url()).searchParams.get('source');
    if (source === 'bf') return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '片源暂时不可用' }) });
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { items: catalog(source), page: 1, pageCount: 1, total: CATALOG_SIZE } }) });
  });
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('片源暂时不可用');
  await page.getByRole('button', { name: '切换360资源', exact: true }).click();
  await expect(page.locator('.drama-card')).toHaveCount(6);
  await page.getByRole('link', { name: '查看全部', exact: true }).click();
  await expect.poll(() => page.locator('.drama-card').count()).toBeGreaterThanOrEqual(24);
  const initialCards = await page.locator('.drama-card').count();
  expect(initialCards).toBeLessThan(CATALOG_SIZE);
  await page.locator('.lazy-sentinel').scrollIntoViewIfNeeded();
  await expect.poll(() => page.locator('.drama-card').count()).toBeGreaterThan(initialCards);
  await expect(page.getByRole('button', { name: '加载更多', exact: true })).toHaveCount(0);
});

test('停用片源的旧播放链接给出提示，不删除本机片单和观看记录', async ({ page }) => {
  const calls = [];
  await page.addInitScript(() => {
    localStorage.setItem('pianke-saved', JSON.stringify(['wj-101']));
    localStorage.setItem('pianke-history', JSON.stringify([{ id: 'wj-101', episode: 1, time: 50, duration: 100 }]));
  });
  page.on('request', (request) => { if (new URL(request.url()).pathname.startsWith('/api/')) calls.push(request.url()); });
  await page.goto('/watch/wj-101');
  await expect(page.getByRole('alert')).toContainText('该片源已停用');
  expect(calls).toEqual([]);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('pianke-saved')))).toEqual(['wj-101']);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('pianke-history'))[0].time)).toBe(50);
});
