import { ItemBand as DefaultItemBand, TimeRange } from '@fqqb/timeline';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { TimelineComponent } from '../timeline.component';

export abstract class BandBase extends DefaultItemBand {
  constructor(
    readonly chart: TimelineComponent,
    protected bandInfo: TimelineBand,
  ) {
    super(chart.timeline);
    this.label = bandInfo.name;
    this.data = { band: bandInfo };
  }

  /**
   * Called when the now-locator moves
   */
  onTick(now: number): void {
    // NOP
  }

  /**
   * Fetch and show new data
   */
  abstract refreshData(
    loadRange: TimeRange,
    visibleRange: TimeRange,
  ): Promise<void>;
}
