import { signal } from '@angular/core';
import { LegendItem } from './LegendItem';

export class Legend {
  private itemsById = new Map<string, LegendItem>();

  /**
   * Fires when items are added/deleted
   */
  itemsSignal = signal<LegendItem[]>([]);

  getItems() {
    return [...this.itemsById.values()];
  }

  addItem(
    traceId: string,
    label: string,
    color: string,
    units: string | null,
    error: string | null,
  ) {
    const item = new LegendItem(traceId, label, color, units, error);
    this.itemsById.set(traceId, item);
    this.fireItemChanges();
  }

  removeItem(traceId: string) {
    this.itemsById.delete(traceId);
    this.fireItemChanges();
  }

  setColor(traceId: string, color: string) {
    const item = this.itemsById.get(traceId);
    if (item) {
      item.color = color;
      this.fireItemChanges();
    }
  }

  setLabel(traceId: string, label: string) {
    const item = this.itemsById.get(traceId);
    if (item) {
      item.label = label;
      this.fireItemChanges();
    }
  }

  setShowUnits(traceId: string, showUnits: boolean) {
    const item = this.itemsById.get(traceId);
    if (item) {
      item.showUnits = showUnits;
      this.fireItemChanges();
    }
  }

  applyOrder(traceIds: string[]) {
    const newItems: LegendItem[] = [];
    for (const traceId of traceIds) {
      const item = this.itemsById.get(traceId);
      if (item) {
        newItems.push(item);
      }
    }

    this.itemsById.clear();
    for (const item of newItems) {
      this.itemsById.set(item.traceId, item);
    }

    this.fireItemChanges();
  }

  private fireItemChanges() {
    const items = [...this.itemsById.values()];
    this.itemsSignal.set(items);
  }
}
