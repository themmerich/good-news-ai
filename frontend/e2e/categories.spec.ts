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

const regularUser = { ...adminUser, username: 'user', displayName: 'Uwe User', role: 'user' };

const sport = { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feedCount: 0 };
const fussball = { id: 'fussball', parentId: 'sport', name: 'Fußball', sortOrder: 0, feedCount: 2 };
const football = { id: 'football', parentId: 'sport', name: 'Football', sortOrder: 1, feedCount: 0 };
const angular = { id: 'angular', parentId: null, name: 'Angular', sortOrder: 1, feedCount: 1 };

test.describe('Categories', () => {
  test.beforeEach(async ({ page }) => {
    // The session is resolved on every page; unanswered with a backend behind the dev server
    // it comes back 401 and the interceptor sends the browser to the login.
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: adminUser }));
    await page.route('**/api/company', (route) => route.fulfill({ json: { name: 'Musterfirma GmbH', hasLogo: false } }));
  });

  test('opens from the sidebar and nests the subcategories', async ({ page }) => {
    await page.route('**/api/categories', (route) => route.fulfill({ json: [sport, fussball, football, angular] }));

    await page.goto('/');
    await page.getByRole('link', { name: 'Kategorien' }).click();

    await expect(page.getByRole('heading', { name: 'Kategorien' })).toBeVisible();
    const blocks = page.locator('[data-category]');
    await expect(blocks).toHaveCount(4);
    await expect(blocks.nth(0)).toContainText('Sport');
    await expect(blocks.nth(1)).toContainText('Fußball');
    await expect(blocks.nth(1)).toContainText('2 Quellen');
  });

  test('creates a subcategory from the button on its parent', async ({ page }) => {
    let created: Record<string, unknown> | undefined;
    await page.route('**/api/categories', (route) => {
      if (route.request().method() === 'POST') {
        created = route.request().postDataJSON() as Record<string, unknown>;
        return route.fulfill({ status: 201, json: { ...fussball, name: 'Handball', id: 'handball' } });
      }
      return route.fulfill({ json: [sport, fussball] });
    });

    await page.goto('/categories');
    await page.getByRole('button', { name: 'Unterkategorie zu Sport anlegen' }).click();
    await page.getByLabel('Name').fill('Handball');
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Kategorie angelegt.')).toBeVisible();
    expect(created).toMatchObject({ name: 'Handball', parentId: 'sport' });
  });

  test('puts a name that a sibling already has under the field, not in a toast', async ({ page }) => {
    await page.route('**/api/categories', (route) => {
      if (route.request().method() === 'POST') {
        return route.fulfill({ status: 409, json: { reason: 'name' } });
      }
      return route.fulfill({ json: [sport, fussball] });
    });

    await page.goto('/categories');
    await page.getByRole('button', { name: 'Neue Kategorie' }).click();
    await page.getByLabel('Name').fill('Sport');
    await page.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Diesen Namen gibt es auf dieser Ebene schon.')).toBeVisible();
    // The dialog stays open so the name can be corrected where it was typed.
    await expect(page.getByRole('dialog')).toBeVisible();
  });

  test('explains a refused delete instead of failing silently', async ({ page }) => {
    await page.route('**/api/categories', (route) => route.fulfill({ json: [sport, fussball] }));
    await page.route('**/api/categories/fussball', (route) => route.fulfill({ status: 409 }));

    await page.goto('/categories');
    await page.getByRole('button', { name: 'Fußball löschen' }).click();
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();

    await expect(page.getByText('An dieser Kategorie hängen noch Quellen.', { exact: false })).toBeVisible();
  });

  test('hides the page from regular users and redirects them away', async ({ page }) => {
    await page.route('**/api/auth/me', (route) => route.fulfill({ json: regularUser }));

    await page.goto('/categories');

    await expect(page.getByRole('heading', { name: 'Testseite' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Kategorien' })).toHaveCount(0);
  });
});
