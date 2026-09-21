import { Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { CardModule } from 'primeng/card';

/**
 * An empty page to try PrimeNG components on. It is the app's start route until
 * the first real feature takes that place.
 */
@Component({
  selector: 'app-test-page',
  imports: [TranslocoDirective, CardModule],
  templateUrl: './test-page.html',
})
export class TestPage {}
