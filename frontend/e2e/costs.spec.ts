import { expect, test } from '@playwright/test';

// Backend-less like the other e2e specs: the API is mocked per test, the
// assertions use the German texts because de is the default language.
const adminUser = {
  username: 'admin',
  displayName: 'Anna Admin',
  role: 'admin',
  tenant: { slug: 'musterfirma', name: 'Musterfirma GmbH' },
};

const summary = {
  day: { cost: 1.5, calls: 2 },
  week: { cost: 3.117, calls: 41 },
  month: { cost: 12.64, calls: 173 },
  year: { cost: 12.64, calls: 173 },
};

function call(id: string, costUsd: number | null) {
  return {
    id,
    calledAt: '2026-09-22T06:28:00Z',
    purpose: 'RATING',
    model: 'claude-sonnet-5',
    inputTokens: 12345,
    outputTokens: 678,
    costUsd,
  };
}

test.describe('Costs', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: adminUser }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
    // The start page is the board, which asks for the categories and the stories.
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: [] }));
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [] }));
    await page.route('**/api/costs/summary', (route) => route.fulfill({ json: summary }));
  });

  test('opens from the sidebar and shows the four periods', async ({ page }) => {
    await page.route(/\/api\/costs\/calls/, (route) =>
      route.fulfill({ json: { calls: [call('a1', 0.03147)], page: 0, size: 25, totalCalls: 1, totalPages: 1 } }),
    );

    await page.goto('/');
    await page.getByRole('link', { name: 'Kosten' }).click();

    await expect(page.getByRole('heading', { name: 'Kosten' })).toBeVisible();
    await expect(page.getByText('Heute')).toBeVisible();
    await expect(page.getByText('Dieses Jahr')).toBeVisible();
    await expect(page.getByText('2 Aufrufe')).toBeVisible();
    // The amounts are in dollars, in German formatting.
    await expect(page.getByText('1,50 $')).toBeVisible();
  });

  test('lists a call with its tokens and what it cost', async ({ page }) => {
    await page.route(/\/api\/costs\/calls/, (route) =>
      route.fulfill({ json: { calls: [call('a1', 0.03147)], page: 0, size: 25, totalCalls: 1, totalPages: 1 } }),
    );

    await page.goto('/costs');

    await expect(page.getByRole('cell', { name: 'claude-sonnet-5' })).toBeVisible();
    await expect(page.getByRole('cell', { name: '12.345' })).toBeVisible();
    await expect(page.getByRole('cell', { name: '0,0315 $' })).toBeVisible();
  });

  test('asks for the next page when the reader pages on', async ({ page }) => {
    const requested: string[] = [];
    await page.route(/\/api\/costs\/calls/, (route) => {
      requested.push(new URL(route.request().url()).search);
      return route.fulfill({
        json: { calls: [call('a1', 0.03147)], page: 0, size: 25, totalCalls: 60, totalPages: 3 },
      });
    });

    await page.goto('/costs');
    await page.getByRole('button', { name: 'Nächste Seite' }).click();

    await expect.poll(() => requested.at(-1)).toContain('page=1');
  });

  test('says so when nothing has been called yet', async ({ page }) => {
    await page.route(/\/api\/costs\/calls/, (route) =>
      route.fulfill({ json: { calls: [], page: 0, size: 25, totalCalls: 0, totalPages: 0 } }),
    );

    await page.goto('/costs');

    await expect(page.getByText('Noch keine Aufrufe.')).toBeVisible();
  });

  test('hides the page from regular users and redirects them away', async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: { ...adminUser, role: 'user' } }));

    await page.goto('/costs');

    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('link', { name: 'Kosten' })).toHaveCount(0);
  });
});
