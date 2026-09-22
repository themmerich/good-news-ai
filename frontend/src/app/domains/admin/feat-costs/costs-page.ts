import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { MessageModule } from 'primeng/message';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

import { CostsService } from '../data/costs-service';

/** One tile: what a period cost, and how many calls that was. */
type Period = {
  key: string;
  label: string;
  cost: number;
  calls: number;
};

/**
 * What the AI costs this tenant: four running periods at the top, every single call below.
 *
 * <p>The amounts are in US dollars, because that is what Anthropic bills in. Converting to euros
 * would need a rate from somewhere and would only make the number less true.
 */
@Component({
  selector: 'app-costs-page',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, TranslocoDirective, MessageModule, TableModule, TagModule],
  templateUrl: './costs-page.html',
})
export class CostsPage {
  protected readonly costsService = inject(CostsService);

  protected readonly pageSize = CostsService.PAGE_SIZE;

  /** The tiles in the order they are read: today first, the year last. */
  protected readonly periods = computed<Period[]>(() => {
    const summary = this.costsService.summary.value();
    return [
      { key: 'day', label: 'costs.day', ...summary.day },
      { key: 'week', label: 'costs.week', ...summary.week },
      { key: 'month', label: 'costs.month', ...summary.month },
      { key: 'year', label: 'costs.year', ...summary.year },
    ];
  });

  /** The table reports where the reader paged to; the service asks the server for that page. */
  protected onPageChange(event: TableLazyLoadEvent): void {
    const size = event.rows ?? this.pageSize;
    this.costsService.showPage(Math.floor((event.first ?? 0) / size), size);
  }
}
