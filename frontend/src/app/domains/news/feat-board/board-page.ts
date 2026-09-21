import { Component, computed, inject, linkedSignal, OnDestroy } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ProgressBarModule } from 'primeng/progressbar';
import { TabsModule } from 'primeng/tabs';

import { ArticlesService } from '../data/articles-service';
import { CatalogService } from '../data/catalog-service';
import { NewsRunStore } from '../data/news-run-store';
import { buildBoard } from '../model/article-board';
import { ArticleCard } from '../ui/article-card';

/** The thresholds worth offering. Finer steps would be a slider nobody asked for. */
const THRESHOLDS = [0, 4, 7, 9];

/** The bucket has no category to be named by, so it needs a value of its own. */
const UNPLACED_TAB = 'unplaced';

/**
 * The board: one tab per category the user ticked and has something in it, and the bucket behind
 * them for what has no category yet.
 *
 * <p>Refreshing is a pass over every source the tenant has, which takes up to a minute, so the
 * button hands over to a progress bar and the store asks after the run. Until the AI stage
 * exists, nothing comes back rated — so in practice everything lands in the bucket, which is the
 * honest picture of where the application stands rather than a bug.
 */
@Component({
  selector: 'app-board-page',
  imports: [TranslocoDirective, ArticleCard, ButtonModule, MessageModule, ProgressBarModule, TabsModule],
  templateUrl: './board-page.html',
})
export class BoardPage implements OnDestroy {
  protected readonly articlesService = inject(ArticlesService);
  protected readonly catalogService = inject(CatalogService);
  protected readonly runStore = inject(NewsRunStore);

  protected readonly thresholds = THRESHOLDS;
  protected readonly unplacedTab = UNPLACED_TAB;

  private readonly picked = computed(
    () =>
      new Set(
        this.catalogService.categories
          .value()
          .filter((category) => category.selected)
          .map((category) => category.id),
      ),
  );

  protected readonly board = computed(() =>
    buildBoard(this.articlesService.articles.value(), this.catalogService.categories.value(), this.picked()),
  );

  protected readonly hasNothingAtAll = computed(() => this.board().tabs.length === 0 && this.board().unplaced.length === 0);

  /** The tabs there are to open, in the order they stand. */
  private readonly tabIds = computed(() => {
    const board = this.board();
    const ids = board.tabs.map((tab) => tab.category.id);
    return board.unplaced.length > 0 ? [...ids, UNPLACED_TAB] : ids;
  });

  /**
   * Which tab is open: the first one to begin with, because a board that opens with nothing
   * selected reads as empty. The reader stays where they were across a reload — a run finishing
   * should not throw them back to the top — and only moves when the tab they were on is gone.
   */
  protected readonly openTab = linkedSignal<string[], string>({
    source: this.tabIds,
    computation: (ids, previous) => (previous !== undefined && ids.includes(previous.value) ? previous.value : (ids[0] ?? '')),
  });

  protected onRefresh(): void {
    void this.runStore.start(() => this.articlesService.reload());
  }

  protected onThresholdChange(event: Event): void {
    this.articlesService.minRanking.set(Number((event.target as HTMLSelectElement).value));
  }

  ngOnDestroy(): void {
    // Leaving the board should not leave a timer behind asking after a run nobody is watching.
    this.runStore.stop();
  }
}
