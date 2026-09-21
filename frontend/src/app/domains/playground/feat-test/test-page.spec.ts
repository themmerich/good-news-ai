import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { TestPage } from './test-page';

const translations = {
  test: {
    title: 'Test page',
    intro: 'A page to try PrimeNG components on.',
  },
};

describe('TestPage', () => {
  let fixture: ComponentFixture<TestPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        TestPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(TestPage);
    await fixture.whenStable();
  });

  it('shows the heading', () => {
    const heading = fixture.nativeElement.querySelector('h1') as HTMLElement;

    expect(heading.textContent?.trim()).toBe('Test page');
  });
});
