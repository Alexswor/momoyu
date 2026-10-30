import { test, expect } from '@playwright/test';

const dramas = ['星海小剧场', '鲸尾奇遇', '海风来信', '月光放映室'].map((title, index) => ({
  id: `preview-${index}`, title, year: 2026, category: '漫剧', tags: ['漫剧', '国语'],
  poster: '/whale/dafeiyu-chibi.png', backdrop: '/whale/dafeiyu-chibi.png',
  compilation: true, originalEpisodeCount: 60, status: '全集版', duration: 3600,
}));

test.beforeEach(async ({ page }) => {
  await page.route('**/api/catalog?**', (route) => route.fulfill({
    contentType: 'application/json', body: JSON.stringify({ code: 200, data: { items: dramas, page: 1, pageCount: 1, total: 4 } }),
  }));
});

test('推荐自动循环，悬停、键盘焦点和暂停按钮均可停止切换', async ({ page }) => {
  await page.clock.install();
  await page.goto('/');
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[0].title);
  await page.clock.pauseAt(new Date(Date.now() + 1000));
  await page.mouse.move(0, 0);
  await page.clock.runFor(6100);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[1].title);

  await page.locator('.hero').hover();
  await page.clock.runFor(12000);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[1].title);
  await page.getByRole('button', { name: `切换至${dramas[3].title}` }).click();
  await page.mouse.move(0, 0);
  await page.clock.runFor(6100);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[3].title);
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).focus();
  await page.clock.runFor(6100);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[0].title);

  await page.getByRole('button', { name: '暂停自动轮播' }).click();
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).focus();
  await page.mouse.move(0, 0);
  await page.clock.runFor(12000);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[0].title);
  await page.getByRole('button', { name: '开启自动轮播' }).click();
  await page.getByRole('textbox', { name: '搜索剧名或类型' }).focus();
  await page.mouse.move(0, 0);
  await page.clock.runFor(6100);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[1].title);
});

test('功能使用不同动作的大肥鱼图标，站名没有绿点，手机无横向溢出', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.hero')).toBeVisible();
  await expect(page.locator('.brand')).toHaveText('摸摸鱼');
  await expect(page.locator('.brand-dot')).toHaveCount(0);
  expect(await page.getByRole('button', { name: '上一个推荐' }).locator('img').evaluate((image) => getComputedStyle(image).scale)).toBe('-1 1');
  expect(await page.getByRole('button', { name: '下一个推荐' }).locator('img').evaluate((image) => getComputedStyle(image).scale)).toBe('none');
  const categoryImages = page.locator('.category-bar .whale-icon__figure');
  expect(new Set(await categoryImages.evaluateAll((images) => images.map((image) => image.src))).size).toBe(6);
  await page.waitForFunction(() => Array.from(document.querySelectorAll('.whale-icon__figure')).every((image) => image.complete && image.naturalWidth > 0));
  await expect(page.locator('.search-form .whale-icon__figure')).toHaveAttribute('src', '/whale/icons/search.webp');
  await page.locator('.source-select').click();
  await expect(page.getByRole('option', { name: '暴风资源' })).toBeVisible();
  await expect(page.getByRole('option', { name: '360资源' })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('减少动态效果偏好下保留手动轮播', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.clock.install();
  await page.goto('/');
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[0].title);
  await page.clock.pauseAt(new Date(Date.now() + 1000));
  await page.clock.runFor(18000);
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[0].title);
  await page.getByRole('button', { name: '下一个推荐' }).click();
  await expect(page.locator('.hero h1')).toHaveAttribute('aria-label', dramas[1].title);
});
