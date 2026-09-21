import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';

import { CatalogService } from '../data/catalog-service';
import { sortCategories } from '../model/category-order';

/**
 * Which categories a user wants on their board. The selection is edited locally and written in
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

  protected readonly categories = computed(() => sortCategories(this.catalogService.categories.value()));

  /** What the backend holds, for telling edited from unedited. */
  private readonly stored = computed(
    () =>
      new Set(
        this.categories()
          .filter((category) => category.selected)
          .map((category) => category.id),
      ),
  );

  /** What is ticked right now. Follows the backend again whenever the list reloads. */
  protected readonly picked = linkedSignal<Set<string>>(() => new Set(this.stored()));

  protected readonly isSaving = signal(false);

  /** Public because the route guard asks before it lets anyone leave with edits pending. */
  readonly hasUnsavedChanges = computed(() => {
    const picked = this.picked();
    const stored = this.stored();
    return picked.size !== stored.size || [...picked].some((id) => !stored.has(id));
  });

  protected readonly pickedCount = computed(() => this.picked().size);

  /** Nothing ticked is not an empty board but the whole of it — the board reads it that way. */
  protected readonly showsEverything = computed(() => this.picked().size === 0);

  protected isPicked(categoryId: string): boolean {
    return this.picked().has(categoryId);
  }

  protected onToggle(categoryId: string): void {
    this.picked.update((current) => {
      const next = new Set(current);
      if (!next.delete(categoryId)) {
        next.add(categoryId);
      }
      return next;
    });
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

  private toast(severity: 'success' | 'error', key: string): void {
    this.messageService.add({ severity, summary: this.transloco.translate(key), life: 4000 });
  }
}
