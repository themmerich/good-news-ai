import { expect, test } from '@playwright/test';

// Backend-less like the other e2e specs: the API is mocked per test, the
// assertions use the German texts because de is the default language.
const adminUser = {
  username: 'admin',
  displayName: 'Anna Admin',
  role: 'admin',
  tenant: { slug: 'musterfirma', name: 'Musterfirma GmbH' },
  hasAvatar: false,
};

const sport = { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feedCount: 0 };
const fussball = { id: 'fussball', parentId: 'sport', name: 'Fußball', sortOrder: 0, feedCount: 1 };

const kicker = {
  id: 'f1',
  categoryId: 'fussball',
  categoryName: 'Fußball',
  name: 'kicker',
  url: 'https://kicker.example/rss',
  type: 'FEED',
};

const nfl = {
  id: 'f2',
  categoryId: 'fussball',
  categoryName: 'Fußball',
  name: 'NFL',
  url: 'https://nfl.example/news/',
  type: 'PAGE',
};

test.describe('Sources', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: adminUser }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
    await page.route('**/api/categories', (route) => route.fulfill({ json: [sport, fussball] }));
  });

  test('opens from the sidebar and says how each source is read', async ({ page }) => {
    await page.route('**/api/feeds', (route) => route.fulfill({ json: [kicker, nfl] }));

    await page.goto('/');
    await page.getByRole('link', { name: 'Quellen' }).click();

    await expect(page.getByRole('heading', { name: 'Quellen' })).toBeVisible();
    await expect(page.getByRole('row', { name: /kicker/ })).toContainText('Feed');
    await expect(page.getByRole('row', { name: /NFL/ })).toContainText('Webseite');
    await expect(page.getByRole('row', { name: /kicker/ }).getByRole('link', { name: kicker.url })).toBeVisible();
  });

  test('searches an ordinary address and creates the feed that was picked', async ({ page }) => {
    let probed: Record<string, unknown> | undefined;
    let created: Record<string, unknown> | undefined;
    await page.route('**/api/feeds/probe', (route) => {
      probed = route.request().postDataJSON() as Record<string, unknown>;
      return route.fulfill({
        json: {
          feeds: [
            { url: 'https://heise.example/rss/heise-atom.xml', title: 'heise online Atom', entryCount: 40 },
            { url: 'https://heise.example/rss/heise.rdf', title: 'heise online RSS', entryCount: 40 },
          ],
        },
      });
    });
    await page.route('**/api/feeds', (route) => {
      if (route.request().method() === 'POST') {
        created = route.request().postDataJSON() as Record<string, unknown>;
        return route.fulfill({ status: 201, json: kicker });
      }
      return route.fulfill({ json: [] });
    });

    await page.goto('/feeds');
    await page.getByRole('button', { name: 'Neue Quelle' }).click();
    // A plain address is what people have to hand; the feed address is the server's job.
    await page.getByLabel('Adresse der Webseite').fill('heise.example');
    await page.getByRole('button', { name: 'Suchen' }).click();

    await expect(page.getByText('2 Feeds gefunden', { exact: false })).toBeVisible();
    await page.getByRole('radio', { name: /heise online RSS/ }).check();
    // The title comes along as a proposal and stays editable.
    await expect(page.getByLabel('Name')).toHaveValue('heise online RSS');
    await page.locator('p-select[inputid="feed-category"]').click();
    await page.getByRole('option', { name: 'Fußball' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Quelle angelegt.')).toBeVisible();
    expect(probed).toEqual({ url: 'heise.example' });
    expect(created).toMatchObject({
      name: 'heise online RSS',
      url: 'https://heise.example/rss/heise.rdf',
      categoryId: 'fussball',
      type: 'FEED',
    });
  });

  test('offers to read the page itself where a site has no feed', async ({ page }) => {
    let created: Record<string, unknown> | undefined;
    await page.route('**/api/feeds/probe', (route) => route.fulfill({ json: { feeds: [] } }));
    await page.route('**/api/feeds', (route) => {
      if (route.request().method() === 'POST') {
        created = route.request().postDataJSON() as Record<string, unknown>;
        return route.fulfill({ status: 201, json: nfl });
      }
      return route.fulfill({ json: [] });
    });

    await page.goto('/feeds');
    await page.getByRole('button', { name: 'Neue Quelle' }).click();
    await page.getByLabel('Adresse der Webseite').fill('https://nfl.example/news/');
    await page.getByRole('button', { name: 'Suchen' }).click();

    await expect(page.getByText('Zu dieser Adresse wurde kein Feed gefunden.')).toBeVisible();
    await page.getByRole('button', { name: 'Seite direkt auslesen' }).click();
    await page.getByLabel('Name').fill('NFL');
    await page.locator('p-select[inputid="feed-category"]').click();
    await page.getByRole('option', { name: 'Fußball' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Quelle angelegt.')).toBeVisible();
    expect(created).toMatchObject({ url: 'https://nfl.example/news/', type: 'PAGE' });
  });

  test('keeps the form shut until a source was picked', async ({ page }) => {
    await page.route('**/api/feeds', (route) => route.fulfill({ json: [] }));

    await page.goto('/feeds');
    await page.getByRole('button', { name: 'Neue Quelle' }).click();

    // Nothing to name and nothing to file away before there is a source.
    await expect(page.getByLabel('Name')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Speichern' })).toBeDisabled();
  });

  test('names the way out when no category can carry a source yet', async ({ page }) => {
    await page.route('**/api/categories', (route) => route.fulfill({ json: [] }));
    await page.route('**/api/feeds', (route) => route.fulfill({ json: [] }));

    await page.goto('/feeds');

    await expect(page.getByText('Es gibt noch keine Kategorie ohne Unterkategorien.', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Neue Quelle' })).toBeDisabled();
  });
});
