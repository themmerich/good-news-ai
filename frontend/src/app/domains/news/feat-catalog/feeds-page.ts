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
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

import { FeedsService } from '../data/feeds-service';
import { Feed, FeedInput, FoundFeed, SourceType } from '../model/category';

type FeedForm = { name: string; url: string };

/** Loose on purpose: it catches a name typed into the URL field, nothing more. */
const URL_SHAPE = /^\s*https?:\/\/\S+\s*$/i;

function isUrlConflict(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 409 && error.error?.reason === 'url';
}

/**
 * The catalog's sources. A source carries no category: it brings several subjects through one
 * address, and which one a story belongs to is settled when that story is rated.
 *
 * <p>Adding one starts from an ordinary web address, not from a feed address: hardly anybody
 * knows those by heart, and sites hide them. The server searches, and what it confirms is offered
 * for picking. Where a site has no feed, the page itself becomes the source.
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
    TableModule,
    TagModule,
    TooltipModule,
  ],
  templateUrl: './feeds-page.html',
})
export class FeedsPage {
  protected readonly feedsService = inject(FeedsService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly editingId = signal<string | null>(null);
  protected readonly isDialogVisible = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly isUrlTaken = signal(false);
  private readonly hasSubmitAttempted = signal(false);

  /** What was typed into the search field, before any of it became a source. */
  protected readonly searchUrl = signal('');
  protected readonly isSearching = signal(false);
  protected readonly searchFailed = signal(false);
  /** Null while nothing was searched for yet; an empty array means the site has no feed. */
  protected readonly foundFeeds = signal<FoundFeed[] | null>(null);
  protected readonly type = signal<SourceType>('FEED');

  protected readonly isEditing = computed(() => this.editingId() !== null);
  /** The search turned up nothing, so the only way on is to read the page itself. */
  protected readonly hasNoFeeds = computed(() => this.foundFeeds()?.length === 0);
  /** Nothing to save before a source was picked or the page itself was taken. */
  protected readonly hasSource = computed(() => this.model().url.trim().length > 0);

  private readonly whenEdited = ({ state }: ChildFieldContext<string>) => state.dirty() || this.hasSubmitAttempted();

  protected readonly model = signal<FeedForm>({ name: '', url: '' });
  protected readonly feedForm = form(this.model, (schemaPath) => {
    required(schemaPath.name, { when: this.whenEdited });
    required(schemaPath.url, { when: this.whenEdited });
    pattern(schemaPath.url, URL_SHAPE, { when: this.whenEdited });
  });

  protected onCreate(): void {
    this.openDialog(null);
  }

  protected onEdit(feed: Feed): void {
    this.openDialog(feed);
  }

  protected onSearchInput(event: Event): void {
    this.searchUrl.set((event.target as HTMLInputElement).value);
    // A new address makes the previous answer meaningless.
    this.foundFeeds.set(null);
    this.searchFailed.set(false);
  }

  protected async onSearch(): Promise<void> {
    const url = this.searchUrl().trim();
    if (url.length === 0) {
      return;
    }
    this.isSearching.set(true);
    this.searchFailed.set(false);
    this.foundFeeds.set(null);
    try {
      this.foundFeeds.set(await this.feedsService.probe(url));
    } catch {
      this.searchFailed.set(true);
    } finally {
      this.isSearching.set(false);
    }
  }

  /** Picking one fills the form with it; its title is a proposal and stays editable. */
  protected onPickFeed(feed: FoundFeed): void {
    this.type.set('FEED');
    this.isUrlTaken.set(false);
    this.model.update((current) => ({ ...current, name: feed.title.trim(), url: feed.url }));
  }

  protected isPicked(feed: FoundFeed): boolean {
    return this.model().url === feed.url;
  }

  /** No feed anywhere, so the address itself becomes the source and is read as a page. */
  protected onTakePage(): void {
    this.type.set('PAGE');
    this.isUrlTaken.set(false);
    this.model.update((current) => ({ ...current, url: this.searchUrl().trim() }));
  }

  protected onUrlInput(): void {
    this.isUrlTaken.set(false);
  }

  protected async onSave(event: Event): Promise<void> {
    event.preventDefault();
    this.hasSubmitAttempted.set(true);
    await submit(this.feedForm, async () => {
      this.isSaving.set(true);
      const editingId = this.editingId();
      try {
        const input: FeedInput = {
          name: this.model().name.trim(),
          url: this.model().url.trim(),
          type: this.type(),
        };
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
    this.model.set({ name: feed?.name ?? '', url: feed?.url ?? '' });
    this.type.set(feed?.type ?? 'FEED');
    this.searchUrl.set('');
    this.foundFeeds.set(null);
    this.searchFailed.set(false);
    this.feedForm().reset();
    this.hasSubmitAttempted.set(false);
    this.isUrlTaken.set(false);
    this.isDialogVisible.set(true);
  }

  private toast(severity: 'success' | 'error', key: string): void {
    this.messageService.add({ severity, summary: this.transloco.translate(key), life: 4000 });
  }
}
