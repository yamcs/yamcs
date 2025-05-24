import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  effect,
  inject,
} from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import {
  AuthService,
  MessageService,
  TimelineBand,
  TrackBySelectionModel,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { TimelineService } from '../timeline.service';

@Component({
  selector: 'app-all-bands-tab',
  templateUrl: './all-bands-tab.component.html',
  styleUrl: './all-bands-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class AllBandsTabComponent {
  private authService = inject(AuthService);
  private changeDetection = inject(ChangeDetectorRef);
  private timelineService = inject(TimelineService);

  displayedColumns = ['name', 'description', 'tags', 'type', 'actions'];

  tableTrackerFn = (index: number, band: TimelineBand) => band.id;

  dataSource = new MatTableDataSource<TimelineBand>();
  selection = new TrackBySelectionModel<TimelineBand>(
    this.tableTrackerFn,
    true,
    [],
  );

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    if (this.mayControlTimeline()) {
      this.displayedColumns.splice(0, 0, 'select');
    }

    effect(() => {
      this.timelineService.refreshTrigger();
      this.refreshData();
    });
  }

  toggleOne(row: TimelineBand) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  private refreshData() {
    this.yamcs.yamcsClient
      .getTimelineBands(this.yamcs.instance!)
      .then((page) => {
        this.selection.matchNewValues(page.bands || []);
        this.dataSource.data = page.bands || [];
        this.changeDetection.markForCheck();
      })
      .catch((err) => this.messageService.showError(err));
  }

  async openCreateBandDialog() {
    const result = await this.timelineService.openCreateBandDialog();
    if (result) {
      this.refreshData();
    }
  }

  openEditBandDialog(band: TimelineBand) {
    this.timelineService.openEditBandDialog(band);
  }

  deleteSelectedBands() {
    if (confirm('Are you sure you want to delete the selected bands?')) {
      for (const band of this.selection.selected) {
        this.deleteBand(band.id, false);
      }
    }
  }

  deleteBand(id: string, prompt = true) {
    if (
      !prompt ||
      confirm('Are you sure you want to delete the selected band?')
    )
      this.yamcs.yamcsClient
        .deleteTimelineBand(this.yamcs.instance!, id)
        .then(() => this.refreshData())
        .catch((err) => this.messageService.showError(err));
  }

  isGroupDeleteEnabled() {
    return !this.selection.isEmpty();
  }

  mayControlTimeline() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }
}
