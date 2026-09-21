import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { MessageService, ToastMessageOptions } from 'primeng/api';

import { CatalogService } from '../data/catalog-service';
import { CatalogCategory } from '../model/category';
import { PicksPage } from './picks-page';

const translations = {
  picks: {
    title: 'My picks',
    intro: 'Choose the sources whose news you want to see.',
    chosen: 'Picked: {{count}}.',
    empty: 'The catalog is still empty.',
    noFeeds: 'No source hangs on this category yet.',
    unsaved: 'Not saved',
    save: 'Save',
    reset: 'Discard',
    saved: 'Picks saved.',
    saveError: 'The picks could not be saved.',
    loadError: 'Could not load the catalog.',
    leaveTitle: 'Leave the page',
    leaveWarning: 'The picks are not saved.',
    leaveAnyway: 'Leave',
    stay: 'Stay here',
  },
};

function catalog(): CatalogCategory[] {
  return [
    { id: 'sport', parentId: null, name: 'Sport', sortOrder: 0, feeds: [] },
    {
      id: 'fussball',
      parentId: 'sport',
      name: 'Fußball',
      sortOrder: 0,
      feeds: [
        { id: 'f1', name: 'kicker', url: 'https://kicker.example/rss', selected: true },
        { id: 'f2', name: 'Sportschau', url: 'https://sportschau.example/rss', selected: false },
      ],
    },
    {
      id: 'angular',
      parentId: null,
      name: 'Angular',
      sortOrder: 1,
      feeds: [{ id: 'f3', name: 'Angular Blog', url: 'https://blog.angular.example/rss', selected: false }],
    },
  ];
}

describe('PicksPage', () => {
  const categories = signal<CatalogCategory[]>([]);
  const error = signal<Error | undefined>(undefined);
  const isLoading = signal(false);
  let toasts: ToastMessageOptions[];
  let saved: string[][];
  let saveError: unknown;

  const catalogServiceStub = {
    catalog: { value: categories, error, isLoading },
    savePicks: (feedIds: string[]) => {
      saved.push(feedIds);
      return saveError ? Promise.reject(saveError) : Promise.resolve();
    },
  } as unknown as CatalogService;

  beforeEach(async () => {
    categories.set(catalog());
    error.set(undefined);
    toasts = [];
    saved = [];
    saveError = undefined;
    await TestBed.configureTestingModule({
      imports: [
        PicksPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        { provide: CatalogService, useValue: catalogServiceStub },
        { provide: MessageService, useValue: { add: (toast: ToastMessageOptions) => toasts.push(toast) } },
      ],
    }).compileComponents();
  });

  function page() {
    const fixture = TestBed.createComponent(PicksPage);
    fixture.detectChanges();
    return fixture;
  }

  it('starts on what the backend holds, with nothing pending', () => {
    const fixture = page();

    expect(fixture.componentInstance['isPicked']('f1')).toBe(true);
    expect(fixture.componentInstance['isPicked']('f2')).toBe(false);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('puts a category that carries subcategories above them, not beside them', () => {
    const fixture = page();
    const element = fixture.nativeElement as HTMLElement;

    // Sport is a heading; Fußball and Angular are the blocks one ticks.
    expect(Array.from(element.querySelectorAll('h2')).map((heading) => heading.textContent?.trim())).toEqual(['Sport']);
    expect(Array.from(element.querySelectorAll('[data-category]')).map((block) => block.getAttribute('data-category'))).toEqual([
      'Fußball',
      'Angular',
    ]);
  });

  it('ticks and unticks a single source, and notices that something is pending', () => {
    const fixture = page();

    fixture.componentInstance['onToggleFeed']('f2');
    expect(fixture.componentInstance['isPicked']('f2')).toBe(true);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);

    fixture.componentInstance['onToggleFeed']('f2');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('takes a whole category, and lets go of it again', () => {
    const fixture = page();
    const fussball = categories().find((category) => category.id === 'fussball')!;

    // One of two is ticked, so the category counts as partly taken and the tick takes the rest.
    expect(fixture.componentInstance['isCategoryPartlyPicked'](fussball)).toBe(true);
    fixture.componentInstance['onToggleCategory'](fussball);
    expect(fixture.componentInstance['isCategoryFullyPicked'](fussball)).toBe(true);

    fixture.componentInstance['onToggleCategory'](fussball);
    expect(fixture.componentInstance['isPicked']('f1')).toBe(false);
    expect(fixture.componentInstance['isPicked']('f2')).toBe(false);
  });

  it('saves the whole selection at once', async () => {
    const fixture = page();
    fixture.componentInstance['onToggleFeed']('f3');

    await fixture.componentInstance['onSave']();

    expect(saved).toHaveLength(1);
    expect([...saved[0]].sort()).toEqual(['f1', 'f3']);
    expect(toasts[0].summary).toBe('Picks saved.');
  });

  it('puts the ticks back where they were on discard', () => {
    const fixture = page();
    fixture.componentInstance['onToggleFeed']('f2');
    fixture.componentInstance['onToggleFeed']('f1');

    fixture.componentInstance['onReset']();

    expect(fixture.componentInstance['isPicked']('f1')).toBe(true);
    expect(fixture.componentInstance['isPicked']('f2')).toBe(false);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('keeps the edits and says so when saving failed', async () => {
    saveError = new Error('offline');
    const fixture = page();
    fixture.componentInstance['onToggleFeed']('f3');

    await fixture.componentInstance['onSave']();

    expect(toasts[0].summary).toBe('The picks could not be saved.');
    expect(fixture.componentInstance['isPicked']('f3')).toBe(true);
  });

  it('says so when the catalog is empty, and when it could not be loaded', () => {
    categories.set([]);
    expect((page().nativeElement as HTMLElement).textContent).toContain('The catalog is still empty.');

    error.set(new Error('offline'));
    expect((page().nativeElement as HTMLElement).textContent).toContain('Could not load the catalog.');
  });
});
