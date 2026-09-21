import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';

import { CatalogService } from '../data/catalog-service';
import { buildTree } from '../model/category-tree';
import { CatalogCategory, CatalogFeed } from '../model/category';

/**
 * What a user takes from their tenant's catalog. The selection is edited locally and written in
 * one go: a click is not worth a request, and the page can then say plainly whether anything is
 * still unsaved.
 *
 * <p>The checkboxes are the browser's own rather than a widget: a list of them is exactly what
 * this is, and nothing here needs more than a label and a tick.
 */
@Component({
  selector: 'app-picks-page',
  imports: [TranslocoDirective, ButtonModule, MessageModule],
  templateUrl: './picks-page.html',
})
export class PicksPage {
  protected readonly catalogService = inject(CatalogService);
  private readonly messageService = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  /**
   * The page shows one block per category that may carry feeds. A top-level category with
   * subcategories becomes a heading over them; one standing alone is such a block itself. Working
   * that out here rather than in the template keeps the markup a plain two-level loop.
   */
  protected readonly groups = computed(() =>
    buildTree(this.catalogService.catalog.value()).map((node) => ({
      id: node.item.id,
      heading: node.children.length === 0 ? null : node.item.name,
      categories: node.children.length === 0 ? [node.item] : node.children.map((child) => child.item),
    })),
  );

  /** What the backend holds, for telling edited from unedited. */
  private readonly stored = computed(
    () =>
      new Set(
        this.feedsOf(this.catalogService.catalog.value())
          .filter((feed) => feed.selected)
          .map((feed) => feed.id),
      ),
  );

  /** What is ticked right now. Follows the backend again whenever the catalog reloads. */
  protected readonly picked = linkedSignal<Set<string>>(() => new Set(this.stored()));

  protected readonly isSaving = signal(false);

  /** Public because the route guard asks before it lets anyone leave with edits pending. */
  readonly hasUnsavedChanges = computed(() => {
    const picked = this.picked();
    const stored = this.stored();
    return picked.size !== stored.size || [...picked].some((id) => !stored.has(id));
  });

  protected readonly pickedCount = computed(() => this.picked().size);

  protected isPicked(feedId: string): boolean {
    return this.picked().has(feedId);
  }

  protected onToggleFeed(feedId: string): void {
    this.picked.update((current) => {
      const next = new Set(current);
      if (!next.delete(feedId)) {
        next.add(feedId);
      }
      return next;
    });
  }

  /** The tick on a category takes all of its feeds, or lets go of all of them. */
  protected onToggleCategory(category: CatalogCategory): void {
    const ids = category.feeds.map((feed) => feed.id);
    const takeAll = !this.isCategoryFullyPicked(category);
    this.picked.update((current) => {
      const next = new Set(current);
      for (const id of ids) {
        if (takeAll) {
          next.add(id);
        } else {
          next.delete(id);
        }
      }
      return next;
    });
  }

  protected isCategoryFullyPicked(category: CatalogCategory): boolean {
    return category.feeds.length > 0 && category.feeds.every((feed) => this.picked().has(feed.id));
  }

  protected isCategoryPartlyPicked(category: CatalogCategory): boolean {
    return !this.isCategoryFullyPicked(category) && category.feeds.some((feed) => this.picked().has(feed.id));
  }

  protected async onSave(): Promise<void> {
    this.isSaving.set(true);
    try {
      await this.catalogService.savePicks([...this.picked()]);
      this.toast('success', 'picks.saved');
    } catch {
      this.toast('error', 'picks.saveError');
    } finally {
      this.isSaving.set(false);
    }
  }

  protected onReset(): void {
    this.picked.set(new Set(this.stored()));
  }

  private feedsOf(categories: CatalogCategory[]): CatalogFeed[] {
    return categories.flatMap((category) => category.feeds);
  }

  private toast(severity: 'success' | 'error', key: string): void {
    this.messageService.add({ severity, summary: this.transloco.translate(key), life: 4000 });
  }
}
