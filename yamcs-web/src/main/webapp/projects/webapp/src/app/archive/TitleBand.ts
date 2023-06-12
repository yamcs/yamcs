import { Banner, Timeline } from '@fqqb/timeline';

export class TitleBand extends Banner {

  constructor(timeline: Timeline, label: string) {
    super(timeline);
    this.label = label;
    this.background = '#f5f5f5';
    this.contentHeight = 20;
  }
}
