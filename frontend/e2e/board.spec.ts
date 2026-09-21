import { expect, test } from '@playwright/test';

// Backend-less like the other e2e specs: the API is mocked per test, the
// assertions use the German texts because de is the default language.
const user = {
  username: 'user',
  displayName: 'Uwe User',
  role: 'user',
  tenant: { slug: 'musterfirma', name: 'Musterfirma GmbH' },
  hasAvatar: false,
};

const categories = [
  { id: 'sport', name: 'Sport', sortOrder: 0, selected: true },
  { id: 'politik', name: 'Politik', sortOrder: 1, selected: true },
];

const placed = {
  id: 'a1',
  categoryId: 'sport',
  categoryName: 'Sport',
  sourceName: 'kicker',
  title: 'Aufstieg in letzter Minute',
  link: 'https://kicker.example/aufstieg',
  publishedAt: '2026-09-20T10:00:00Z',
  teaser: 'Ein Tor in der Nachspielzeit entscheidet die Saison.',
  positiveSummary: null,
  ranking: 8,
};

const unplaced = {
  id: 'a2',
  categoryId: null,
  categoryName: null,
  sourceName: 't-online',
  title: 'Noch nicht einsortiert',
  link: 'https://t-online.example/meldung',
  publishedAt: '2026-09-20T09:00:00Z',
  teaser: 'Die KI hat diese Meldung noch nicht gesehen.',
  positiveSummary: null,
  ranking: null,
};

test.describe('Board', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: user }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
    await page.route('**/api/news/categories', (route) => route.fulfill({ json: categories }));
  });

  test('is the start page, and the sidebar links to it', async ({ page }) => {
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [placed] }));

    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Übersicht' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Übersicht' })).toBeVisible();
  });

  test('puts a story in the tab of its category', async ({ page }) => {
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [placed] }));

    await page.goto('/');

    await expect(page.getByRole('tab', { name: /Sport \(1\)/ })).toBeVisible();
    // Politik is ticked but empty; a tab that leads nowhere is not worth the click.
    await expect(page.getByRole('tab', { name: /Politik/ })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Aufstieg in letzter Minute' })).toBeVisible();
  });

  /**
   * Until the AI stage exists nothing comes back rated, so in practice the whole board sits in
   * this tab. It is the honest picture of where the application stands rather than a fault.
   */
  test('collects what has no category under Sonstiges, with a word on why', async ({ page }) => {
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [placed, unplaced] }));

    await page.goto('/');
    await page.getByRole('tab', { name: /Sonstiges \(1\)/ }).click();

    await expect(page.getByText('Meldungen ohne Kategorie', { exact: false })).toBeVisible();
    // Scoped to the card: the hint above the list says the same words about the whole tab.
    await expect(page.getByRole('article').getByText('noch nicht bewertet')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Noch nicht einsortiert' })).toBeVisible();
  });

  test('says so while nothing has been fetched yet', async ({ page }) => {
    await page.route(/\/api\/news\/articles/, (route) => route.fulfill({ json: [] }));

    await page.goto('/');

    await expect(page.getByText('Noch keine Meldungen.', { exact: false })).toBeVisible();
  });

  test('shows the progress of a run and loads the stories once it is through', async ({ page }) => {
    let articleCalls = 0;
    await page.route(/\/api\/news\/articles/, (route) => {
      articleCalls += 1;
      return route.fulfill({ json: articleCalls === 1 ? [] : [placed] });
    });
    await page.route('**/api/news/runs', (route) =>
      route.fulfill({
        status: 202,
        json: { id: 'r1', status: 'RUNNING', totalArticles: 0, processedArticles: 0, startedAt: '2026-09-21T10:00:00Z' },
      }),
    );
    // The first ask still says running, the second says it is done.
    let runCalls = 0;
    await page.route('**/api/news/runs/r1', (route) => {
      runCalls += 1;
      return route.fulfill({
        json: {
          id: 'r1',
          status: runCalls === 1 ? 'RUNNING' : 'DONE',
          totalArticles: 2,
          processedArticles: runCalls === 1 ? 1 : 2,
          startedAt: '2026-09-21T10:00:00Z',
          finishedAt: runCalls === 1 ? null : '2026-09-21T10:00:30Z',
        },
      });
    });

    await page.goto('/');
    await page.getByRole('button', { name: 'Aktualisieren' }).click();

    await expect(page.getByText('Die Quellen werden geholt')).toBeVisible();
    // Two polls two seconds apart, so the wait has to allow for both.
    await expect(page.getByRole('tab', { name: /Sport \(1\)/ })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('Die Quellen werden geholt')).toHaveCount(0);
  });

  test('passes the chosen threshold to the backend', async ({ page }) => {
    const asked: string[] = [];
    await page.route(/\/api\/news\/articles/, (route) => {
      asked.push(new URL(route.request().url()).searchParams.get('minRanking') ?? '');
      return route.fulfill({ json: [placed] });
    });

    await page.goto('/');
    await page.getByLabel('Ab Ranking').selectOption('7');

    await expect.poll(() => asked).toContain('7');
  });
});
