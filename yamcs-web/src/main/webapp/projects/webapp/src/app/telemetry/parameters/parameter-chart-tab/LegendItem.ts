import { signal } from '@angular/core';

export class LegendItem {
  value = signal<string | null>(null);

  // Should be set to false when showing raw values
  showUnits = true;

  constructor(
    public traceId: string,
    public label: string,
    public color: string,
    public units: string | null,
    public error: string | null,
  ) {}
}
