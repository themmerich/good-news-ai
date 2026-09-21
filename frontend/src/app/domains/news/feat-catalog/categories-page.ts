import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ChildFieldContext, form, FormField, required, submit } from '@angular/forms/signals';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';

import { CategoriesService } from '../data/categories-service';
import { positionOf, sortCategories } from '../model/category-order';
import { Category, CategoryInput } from '../model/category';

type CategoryForm = { name: string };

function isNameConflict(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 409 && error.error?.reason === 'name';
}

/**
 * The catalog's categories: one flat list, created and renamed in a dialog, moved with the
 * buttons on each row. Their order is the order of the tabs on the board.
 *
 * <p>Moving is done with up and down rather than by dragging. The list is a handful of rows, a
 * drag would be unreachable by keyboard, and the same endpoint serves both — so dragging can be
 * added later without anything else changing.
 */
@Component({
  selector: 'app-categories-page',
  imports: [FormField, TranslocoDirective, ButtonModule, DialogModule, FloatLabelModule, InputTextModule, MessageModule, TooltipModule],
  templateUrl: './categories-page.html',
})
export class CategoriesPage {
  protected readonly categoriesService = inject(CategoriesService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly categories = computed(() => sortCategories(this.categoriesService.categories.value()));

  protected readonly editingId = signal<string | null>(null);
  protected readonly isDialogVisible = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly isNameTaken = signal(false);
  protected readonly movingId = signal<string | null>(null);
  // A save attempt judges the field at once — submit() alone does not flip its touched state.
  private readonly hasSubmitAttempted = signal(false);

  // PrimeNG paints an invalid field red the moment it is bound, so the name is only judged once
  // it was typed into or a save was attempted.
  private readonly whenEdited = ({ state }: ChildFieldContext<string>) => state.dirty() || this.hasSubmitAttempted();

  protected readonly model = signal<CategoryForm>({ name: '' });
  protected readonly categoryForm = form(this.model, (schemaPath) => {
    required(schemaPath.name, { when: this.whenEdited });
  });

  protected onCreate(): void {
    this.openDialog(null);
  }

  protected onEdit(category: Category): void {
    this.openDialog(category);
  }

  protected async onSave(event: Event): Promise<void> {
    event.preventDefault();
    this.hasSubmitAttempted.set(true);
    await submit(this.categoryForm, async () => {
      this.isSaving.set(true);
      const editingId = this.editingId();
      try {
        const input: CategoryInput = { name: this.model().name.trim() };
        if (editingId === null) {
          await this.categoriesService.create(input);
        } else {
          await this.categoriesService.update(editingId, input);
        }
        this.isDialogVisible.set(false);
        this.toast('success', editingId === null ? 'categories.created' : 'categories.saved');
      } catch (error) {
        if (isNameConflict(error)) {
          // Theirs to fix, under the field rather than in a toast; the dialog stays open.
          this.isNameTaken.set(true);
        } else {
          this.toast('error', editingId === null ? 'categories.createError' : 'categories.saveError');
        }
      } finally {
        this.isSaving.set(false);
      }
    });
  }

  protected onNameInput(): void {
    this.isNameTaken.set(false);
  }

  protected async onMove(category: Category, by: -1 | 1): Promise<void> {
    const target = this.targetPosition(category, by);
    if (target === null) {
      return;
    }
    this.movingId.set(category.id);
    try {
      await this.categoriesService.update(category.id, { name: category.name, sortOrder: target });
    } catch {
      this.toast('error', 'categories.saveError');
    } finally {
      this.movingId.set(null);
    }
  }

  protected canMove(category: Category, by: -1 | 1): boolean {
    return this.targetPosition(category, by) !== null;
  }

  protected onDelete(category: Category): void {
    this.confirmationService.confirm({
      header: this.transloco.translate('categories.deleteTitle'),
      message: this.transloco.translate('categories.deleteWarning', { name: category.name }),
      acceptLabel: this.transloco.translate('categories.delete'),
      rejectLabel: this.transloco.translate('categories.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => void this.remove(category),
    });
  }

  private async remove(category: Category): Promise<void> {
    try {
      await this.categoriesService.remove(category.id);
      this.toast('success', 'categories.deleted');
    } catch {
      this.toast('error', 'categories.deleteError');
    }
  }

  /** Where the category would land, or null where that is off either end of the list. */
  private targetPosition(category: Category, by: -1 | 1): number | null {
    const position = positionOf(this.categories(), category.id);
    if (position === null) {
      return null;
    }
    const target = position.index + by;
    return target >= 0 && target < position.count ? target : null;
  }

  private openDialog(category: Category | null): void {
    this.editingId.set(category?.id ?? null);
    this.model.set({ name: category?.name ?? '' });
    this.categoryForm().reset();
    this.hasSubmitAttempted.set(false);
    this.isNameTaken.set(false);
    this.isDialogVisible.set(true);
  }

  private toast(severity: 'success' | 'error', key: string): void {
    this.messageService.add({ severity, summary: this.transloco.translate(key), life: 4000 });
  }
}
