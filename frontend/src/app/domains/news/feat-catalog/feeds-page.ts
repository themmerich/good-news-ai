import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ChildFieldContext, form, FormField, pattern, required, submit } from '@angular/forms/signals';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';

import { CategoriesService } from '../data/categories-service';
import { FeedsService } from '../data/feeds-service';
import { buildTree, leaves } from '../model/category-tree';
import { Feed, FeedInput } from '../model/category';

type FeedForm = { name: string; url: string; categoryId: string | null };

/** Loose on purpose: it catches a name typed into the URL field, nothing more. */
const URL_SHAPE = /^\s*https?:\/\/\S+\s*$/i;

function isUrlConflict(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 409 && error.error?.reason === 'url';
}

/**
 * The catalog's feeds. A feed hangs on a category without subcategories, because such a category
 * is what becomes a tab — so the dialog offers only those.
 */
@Component({
  selector: 'app-feeds-page',
  imports: [
    FormField,
    TranslocoDirective,
    ButtonModule,
    DialogModule,
    FloatLabelModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    TableModule,
    TooltipModule,
  ],
  templateUrl: './feeds-page.html',
})
export class FeedsPage {
  protected readonly feedsService = inject(FeedsService);
  protected readonly categoriesService = inject(CategoriesService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  /** Only categories without subcategories: the others have no tab to show a feed on. */
  protected readonly categoryOptions = computed(() =>
    leaves(buildTree(this.categoriesService.categories.value())).map((category) => ({
      label: category.name,
      value: category.id,
    })),
  );

  protected readonly editingId = signal<string | null>(null);
  protected readonly isDialogVisible = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly isUrlTaken = signal(false);
  private readonly hasSubmitAttempted = signal(false);

  private readonly whenEdited = ({ state }: ChildFieldContext<string>) => state.dirty() || this.hasSubmitAttempted();

  protected readonly model = signal<FeedForm>({ name: '', url: '', categoryId: null });
  protected readonly feedForm = form(this.model, (schemaPath) => {
    required(schemaPath.name, { when: this.whenEdited });
    required(schemaPath.url, { when: this.whenEdited });
    pattern(schemaPath.url, URL_SHAPE, { when: this.whenEdited });
    required(schemaPath.categoryId, { when: () => this.hasSubmitAttempted() });
  });

  protected onCreate(): void {
    this.openDialog(null);
  }

  protected onEdit(feed: Feed): void {
    this.openDialog(feed);
  }

  protected onUrlInput(): void {
    this.isUrlTaken.set(false);
  }

  protected async onSave(event: Event): Promise<void> {
    event.preventDefault();
    this.hasSubmitAttempted.set(true);
    await submit(this.feedForm, async () => {
      const categoryId = this.model().categoryId;
      if (categoryId === null) {
        return;
      }
      this.isSaving.set(true);
      const editingId = this.editingId();
      try {
        const input: FeedInput = { name: this.model().name.trim(), url: this.model().url.trim(), categoryId };
        if (editingId === null) {
          await this.feedsService.create(input);
        } else {
          await this.feedsService.update(editingId, input);
        }
        this.isDialogVisible.set(false);
        this.toast('success', editingId === null ? 'feeds.created' : 'feeds.saved');
      } catch (error) {
        if (isUrlConflict(error)) {
          this.isUrlTaken.set(true);
        } else {
          this.toast('error', editingId === null ? 'feeds.createError' : 'feeds.saveError');
        }
      } finally {
        this.isSaving.set(false);
      }
    });
  }

  protected onDelete(feed: Feed): void {
    this.confirmationService.confirm({
      header: this.transloco.translate('feeds.deleteTitle'),
      message: this.transloco.translate('feeds.deleteWarning', { name: feed.name }),
      acceptLabel: this.transloco.translate('feeds.delete'),
      rejectLabel: this.transloco.translate('feeds.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => void this.remove(feed),
    });
  }

  private async remove(feed: Feed): Promise<void> {
    try {
      await this.feedsService.remove(feed.id);
      this.toast('success', 'feeds.deleted');
    } catch {
      this.toast('error', 'feeds.deleteError');
    }
  }

  private openDialog(feed: Feed | null): void {
    this.editingId.set(feed?.id ?? null);
    this.model.set({
      name: feed?.name ?? '',
      url: feed?.url ?? '',
      categoryId: feed?.categoryId ?? null,
    });
    this.feedForm().reset();
    this.hasSubmitAttempted.set(false);
    this.isUrlTaken.set(false);
    this.isDialogVisible.set(true);
  }

  private toast(severity: 'success' | 'error', key: string): void {
    this.messageService.add({ severity, summary: this.transloco.translate(key), life: 4000 });
  }
}
