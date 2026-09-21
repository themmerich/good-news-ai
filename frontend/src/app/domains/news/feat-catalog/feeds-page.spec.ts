import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Confirmation, ConfirmationService, MessageService, ToastMessageOptions } from 'primeng/api';

import { CategoriesService } from '../data/categories-service';
import { FeedsService } from '../data/feeds-service';
import { Category, Feed, FeedInput, FoundFeed } from '../model/category';
import { FeedsPage } from './feeds-page';

const translations = {
  feeds: {
    title: 'Sources',
    intro: 'The feeds the news are fetched from.',
    empty: 'No source yet.',
    noCategories: 'There is no category without subcategories yet.',
    create: 'New source',
    createTitle: 'New source',
    editTitle: 'Edit source',
    edit: 'Edit {{name}}',
    name: 'Name',
    nameRequired: 'Please enter a name.',
    url: 'Feed address',
    urlHint: 'Starting with http:// or https://.',
    urlInvalid: 'That does not look like an address.',
    urlTaken: 'That address is already in the catalog.',
    category: 'Category',
    categoryRequired: 'Please choose a category.',
    actions: 'Actions',
    save: 'Save',
    cancel: 'Cancel',
    delete: 'Delete',
    deleteNamed: 'Delete {{name}}',
    deleteTitle: 'Delete source',
    deleteWarning: '{{name}} will be deleted.',
    created: 'Source created.',
    saved: 'Source saved.',
    deleted: 'Source deleted.',
    createError: 'The source could not be created.',
    saveError: 'The source could not be saved.',
    deleteError: 'The source could not be deleted.',
    loadError: 'Could not load the sources.',
    type: 'Type',
    typeFeed: 'Feed',
    typePage: 'Web page',
    typeFeedHint: 'The address of the feed that is read.',
    typePageHint: 'This page is read directly.',
    searchUrl: 'Address of the website',
    search: 'Search',
    searchHint: 'The ordinary address is enough.',
    searchError: 'The address could not be checked.',
    foundCount: '{{count}} feeds found',
    entryCount: '{{count}} entries',
    noFeedFound: 'No feed was found behind this address.',
    takePage: 'Read the page directly',
  },
};

const sport: Category = { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feedCount: 0 };
const fussball: Category = { id: 'fussball', parentId: 'sport', name: 'Fußball', sortOrder: 0, feedCount: 1 };
const angular: Category = { id: 'angular', parentId: null, name: 'Angular', sortOrder: 1, feedCount: 0 };

const kicker: Feed = {
  id: 'f1',
  categoryId: 'fussball',
  categoryName: 'Fußball',
  name: 'kicker',
  url: 'https://kicker.example/rss',
  type: 'FEED',
};

describe('FeedsPage', () => {
  const feeds = signal<Feed[]>([]);
  const categories = signal<Category[]>([]);
  const feedsError = signal<Error | undefined>(undefined);
  const isLoading = signal(false);
  let toasts: ToastMessageOptions[];
  let created: FeedInput[];
  let updated: { id: string; input: FeedInput }[];
  let removed: string[];
  let saveError: unknown;
  let probed: string[];
  let probeResult: FoundFeed[];
  let probeError: unknown;

  const feedsServiceStub = {
    feeds: { value: feeds, error: feedsError, isLoading },
    probe: (url: string) => {
      probed.push(url);
      return probeError ? Promise.reject(probeError) : Promise.resolve(probeResult);
    },
    create: (input: FeedInput) => {
      created.push(input);
      return saveError ? Promise.reject(saveError) : Promise.resolve();
    },
    update: (id: string, input: FeedInput) => {
      updated.push({ id, input });
      return saveError ? Promise.reject(saveError) : Promise.resolve();
    },
    remove: (id: string) => {
      removed.push(id);
      return Promise.resolve();
    },
  } as unknown as FeedsService;

  const categoriesServiceStub = {
    categories: { value: categories, error: signal(undefined), isLoading },
  } as unknown as CategoriesService;

  const confirmationServiceStub = {
    confirm: (confirmation: Confirmation) => confirmation.accept?.(),
  } as unknown as ConfirmationService;

  beforeEach(async () => {
    feeds.set([kicker]);
    categories.set([sport, fussball, angular]);
    feedsError.set(undefined);
    toasts = [];
    created = [];
    updated = [];
    removed = [];
    saveError = undefined;
    probed = [];
    probeResult = [];
    probeError = undefined;
    await TestBed.configureTestingModule({
      imports: [
        FeedsPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        { provide: FeedsService, useValue: feedsServiceStub },
        { provide: CategoriesService, useValue: categoriesServiceStub },
        { provide: ConfirmationService, useValue: confirmationServiceStub },
        { provide: MessageService, useValue: { add: (toast: ToastMessageOptions) => toasts.push(toast) } },
      ],
    }).compileComponents();
  });

  function page() {
    const fixture = TestBed.createComponent(FeedsPage);
    fixture.detectChanges();
    return fixture;
  }

  it('lists the feeds with the category they hang on', () => {
    const element = page().nativeElement as HTMLElement;

    expect(element.textContent).toContain('kicker');
    expect(element.textContent).toContain('Fußball');
    expect(element.querySelector('a[href="https://kicker.example/rss"]')).not.toBeNull();
  });

  it('offers only the categories that carry no subcategories', () => {
    const fixture = page();

    // Sport has Fußball under it, so a feed there would have no tab to appear on.
    expect(fixture.componentInstance['categoryOptions']().map((option) => option.label)).toEqual(['Fußball', 'Angular']);
  });

  it('names the way out when there is no category to hang a feed on', () => {
    categories.set([]);

    expect((page().nativeElement as HTMLElement).textContent).toContain('There is no category without subcategories yet.');
  });

  it('searches an ordinary address and creates the feed that was picked', async () => {
    probeResult = [{ url: 'https://kicker.example/rss', title: '  kicker News  ', entryCount: 12 }];
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['searchUrl'].set('  kicker.example  ');

    await fixture.componentInstance['onSearch']();
    fixture.detectChanges();

    // The address goes out as typed but without the padding; the feed address is the server's job.
    expect(probed).toEqual(['kicker.example']);
    fixture.componentInstance['onPickFeed'](probeResult[0]);
    fixture.componentInstance['model'].update((current) => ({ ...current, categoryId: 'fussball' }));
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));

    expect(created).toEqual([{ name: 'kicker News', url: 'https://kicker.example/rss', categoryId: 'fussball', type: 'FEED' }]);
    expect(toasts[0].summary).toBe('Source created.');
  });

  it('offers to read the page itself where a site has no feed', async () => {
    probeResult = [];
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['searchUrl'].set('https://nfl.example/news/');

    await fixture.componentInstance['onSearch']();
    fixture.detectChanges();

    expect(fixture.componentInstance['hasNoFeeds']()).toBe(true);
    fixture.componentInstance['onTakePage']();
    fixture.componentInstance['model'].update((current) => ({ ...current, name: 'NFL', categoryId: 'fussball' }));
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));

    expect(created).toEqual([{ name: 'NFL', url: 'https://nfl.example/news/', categoryId: 'fussball', type: 'PAGE' }]);
  });

  it('says so when the search itself went wrong, which is not the same as finding nothing', async () => {
    probeError = new Error('offline');
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['searchUrl'].set('kaputt.example');

    await fixture.componentInstance['onSearch']();
    fixture.detectChanges();

    expect(fixture.componentInstance['searchFailed']()).toBe(true);
    // Nothing to offer and nothing to take: the page does not pretend the site has no feed.
    expect(fixture.componentInstance['hasNoFeeds']()).toBe(false);
  });

  it('forgets an earlier answer as soon as the address changes', async () => {
    probeResult = [{ url: 'https://kicker.example/rss', title: 'kicker', entryCount: 3 }];
    const fixture = page();
    fixture.componentInstance['onCreate']();
    fixture.componentInstance['searchUrl'].set('kicker.example');
    await fixture.componentInstance['onSearch']();

    const input = document.createElement('input');
    input.value = 'golem.example';
    fixture.componentInstance['onSearchInput']({ target: input } as unknown as Event);

    expect(fixture.componentInstance['foundFeeds']()).toBeNull();
  });

  it('names a taken address under the field and leaves the dialog open', async () => {
    saveError = new HttpErrorResponse({ status: 409, error: { reason: 'url' } });
    const fixture = page();
    fixture.componentInstance['onEdit'](kicker);
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance['isUrlTaken']()).toBe(true);
    expect(fixture.componentInstance['isDialogVisible']()).toBe(true);
  });

  it('never calls the backend for something that is no address', async () => {
    const fixture = page();
    fixture.componentInstance['onEdit'](kicker);
    fixture.componentInstance['model'].set({ name: 'kicker', url: 'kicker.example', categoryId: 'fussball' });
    fixture.detectChanges();

    await fixture.componentInstance['onSave'](new Event('submit'));

    expect(created).toHaveLength(0);
  });

  it('shows which sources are read as a page rather than as a feed', () => {
    feeds.set([kicker, { ...kicker, id: 'f2', name: 'NFL', url: 'https://nfl.example/news/', type: 'PAGE' }]);

    const element = page().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Feed');
    expect(element.textContent).toContain('Web page');
  });

  it('deletes a feed once the question was answered', async () => {
    const fixture = page();

    fixture.componentInstance['onDelete'](kicker);
    await fixture.whenStable();

    expect(removed).toEqual(['f1']);
    expect(toasts[0].summary).toBe('Source deleted.');
  });

  it('shows the load error instead of an empty table', () => {
    feedsError.set(new Error('offline'));

    expect((page().nativeElement as HTMLElement).textContent).toContain('Could not load the sources.');
  });
});
