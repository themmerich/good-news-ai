import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { MessageService, ToastMessageOptions } from 'primeng/api';

import { CatalogService } from '../data/catalog-service';
import { PickedCategory } from '../model/category';
import { PicksPage } from './picks-page';

const translations = {
  picks: {
    title: 'My picks',
    intro: 'Tick the categories you want to see on the board.',
    chosen: 'Picked: {{count}}.',
    showingAll: 'Nothing ticked — you see every category.',
    noCategories: 'No category yet.',
    displayOnlyHint: 'The choice only orders what is shown.',
    unsaved: 'Not saved',
    save: 'Save',
    reset: 'Discard',
    saved: 'Picks saved.',
    saveError: 'The picks could not be saved.',
    loadError: 'Could not load the categories.',
    leaveTitle: 'Leave the page',
    leaveWarning: 'The picks are not saved.',
    leaveAnyway: 'Leave',
    stay: 'Stay here',
  },
};

function catalog(): PickedCategory[] {
  return [
    { id: 'sport', name: 'Sport', sortOrder: 0, selected: true },
    { id: 'politik', name: 'Politik', sortOrder: 1, selected: false },
    { id: 'soziales', name: 'Soziales', sortOrder: 2, selected: false },
  ];
}

describe('PicksPage', () => {
  const categories = signal<PickedCategory[]>([]);
  const error = signal<Error | undefined>(undefined);
  const isLoading = signal(false);
  let toasts: ToastMessageOptions[];
  let saved: string[][];
  let saveError: unknown;

  const catalogServiceStub = {
    categories: { value: categories, error, isLoading },
    savePicks: (categoryIds: string[]) => {
      saved.push(categoryIds);
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

    expect(fixture.componentInstance['isPicked']('sport')).toBe(true);
    expect(fixture.componentInstance['isPicked']('politik')).toBe(false);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('lists the categories in their position order, one tick each', () => {
    categories.set([
      { id: 'soziales', name: 'Soziales', sortOrder: 2, selected: false },
      { id: 'sport', name: 'Sport', sortOrder: 0, selected: true },
      { id: 'politik', name: 'Politik', sortOrder: 1, selected: false },
    ]);
    const element = page().nativeElement as HTMLElement;

    expect(Array.from(element.querySelectorAll('[data-category]')).map((row) => row.getAttribute('data-category'))).toEqual([
      'Sport',
      'Politik',
      'Soziales',
    ]);
    expect(element.querySelectorAll('input[type="checkbox"]')).toHaveLength(3);
  });

  it('ticks and unticks a category, and notices that something is pending', () => {
    const fixture = page();

    fixture.componentInstance['onToggle']('politik');
    expect(fixture.componentInstance['isPicked']('politik')).toBe(true);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);

    fixture.componentInstance['onToggle']('politik');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  /** Nothing ticked is the whole board rather than an empty one, and the page has to say so. */
  it('reads an empty selection as everything', () => {
    const fixture = page();

    fixture.componentInstance['onToggle']('sport');

    expect(fixture.componentInstance['showsEverything']()).toBe(true);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nothing ticked — you see every category.');
  });

  it('says that the choice saves nothing, only orders the board', () => {
    expect((page().nativeElement as HTMLElement).textContent).toContain('The choice only orders what is shown.');
  });

  it('saves the whole selection at once', async () => {
    const fixture = page();
    fixture.componentInstance['onToggle']('soziales');

    await fixture.componentInstance['onSave']();

    expect(saved).toHaveLength(1);
    expect([...saved[0]].sort()).toEqual(['soziales', 'sport']);
    expect(toasts[0].summary).toBe('Picks saved.');
  });

  it('puts the ticks back where they were on discard', () => {
    const fixture = page();
    fixture.componentInstance['onToggle']('politik');
    fixture.componentInstance['onToggle']('sport');

    fixture.componentInstance['onReset']();

    expect(fixture.componentInstance['isPicked']('sport')).toBe(true);
    expect(fixture.componentInstance['isPicked']('politik')).toBe(false);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('keeps the edits and says so when saving failed', async () => {
    saveError = new Error('offline');
    const fixture = page();
    fixture.componentInstance['onToggle']('soziales');

    await fixture.componentInstance['onSave']();

    expect(toasts[0].summary).toBe('The picks could not be saved.');
    expect(fixture.componentInstance['isPicked']('soziales')).toBe(true);
  });

  it('says so when there is no category yet, and when they could not be loaded', () => {
    categories.set([]);
    expect((page().nativeElement as HTMLElement).textContent).toContain('No category yet.');

    error.set(new Error('offline'));
    expect((page().nativeElement as HTMLElement).textContent).toContain('Could not load the categories.');
  });
});
