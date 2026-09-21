import { ApplicationRef, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ArticlesService, MIN_RANKING_STORAGE } from './articles-service';

/** jsdom does not reliably offer a working localStorage, so the service takes one by injection. */
function fakeStorage(initial: Record<string, string> = {}): Storage {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => void values.set(key, value),
    removeItem: (key: string) => void values.delete(key),
    clear: () => values.clear(),
    key: (index: number) => [...values.keys()][index] ?? null,
    get length() {
      return values.size;
    },
  } as Storage;
}

function serviceWith(storage: Storage): ArticlesService {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MIN_RANKING_STORAGE, useValue: storage },
    ],
  });
  return TestBed.inject(ArticlesService);
}

describe('ArticlesService', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('starts at the lowest threshold when nothing was stored', () => {
    expect(serviceWith(fakeStorage()).minRanking()).toBe(0);
  });

  /** Somebody who reads only what matters should not have to say so again every morning. */
  it('picks up the threshold the browser remembered', () => {
    expect(serviceWith(fakeStorage({ 'good-news-min-ranking': '7' })).minRanking()).toBe(7);
  });

  it('writes the threshold down when it changes', () => {
    const storage = fakeStorage();
    const service = serviceWith(storage);

    service.minRanking.set(9);
    // tick() rather than whenStable(): the service holds an httpResource whose request nobody
    // answers here, so the application never goes quiet, while effects flush either way.
    TestBed.inject(ApplicationRef).tick();

    expect(storage.getItem('good-news-min-ranking')).toBe('9');
  });

  /**
   * A value that is not one of the offered steps is drift, from an older version or a hand edit.
   * Falling back beats filtering on something the page cannot unset.
   */
  it('ignores a stored value that is not one of the steps', () => {
    expect(serviceWith(fakeStorage({ 'good-news-min-ranking': '5' })).minRanking()).toBe(0);
    TestBed.resetTestingModule();
    expect(serviceWith(fakeStorage({ 'good-news-min-ranking': 'irgendwas' })).minRanking()).toBe(0);
  });
});
