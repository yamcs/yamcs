import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { MatTabGroup } from '@angular/material/tabs';
import { Preferences, TimelineView, WebappSdkModule } from '@yamcs/webapp-sdk';
import { PREF_DETAIL_TAB } from '../preferences';
import { DateRange } from '../timeline.component';
import { AllBandsTabComponent } from './all-bands-tab.component';
import { ItemsTabComponent } from './items-tab.component';
import { ViewConfigurationTabComponent } from './view-configuration-tab.component';

@Component({
  selector: 'app-timeline-detail',
  templateUrl: './timeline-detail.component.html',
  styleUrl: './timeline-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AllBandsTabComponent,
    ItemsTabComponent,
    ViewConfigurationTabComponent,
    WebappSdkModule,
  ],
})
export class TimelineDetail implements AfterViewInit {
  private prefs = inject(Preferences);

  view = input<TimelineView | null>();
  viewportRange = input<DateRange | null>();
  tabGroup = viewChild.required(MatTabGroup);

  ngAfterViewInit(): void {
    this.selectPreferredTab();
  }

  private selectPreferredTab() {
    // Remove final stretch tab, we never want to select that
    const tabCount = this.tabGroup()._allTabs.length - 1;
    const maxIndex = tabCount - 1;

    // Restore previously selected tab on refresh or page navigation
    // (not something we want in the URL because it's a personal preference).
    this.tabGroup().selectedIndex = Math.min(
      this.prefs.getNumber(PREF_DETAIL_TAB, 0),
      maxIndex,
    );
  }

  onIndexChange(index: number) {
    this.prefs.setNumber(PREF_DETAIL_TAB, index);
  }
}
