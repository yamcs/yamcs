import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
} from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Router } from '@angular/router';
import {
  AuthService,
  MessageService,
  TimelineBand,
  TimelineView,
  UpdateTimelineViewRequest,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { RenameViewDialogComponent } from '../dialogs/rename-view-dialog.component';
import { SelectBandDialogComponent } from '../dialogs/select-band-dialog.component';
import { TimelineService } from '../timeline.service';

@Component({
  selector: 'app-view-configuration-tab',
  templateUrl: './view-configuration-tab.component.html',
  styleUrl: './view-configuration-tab.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ViewConfigurationTabComponent {
  private authService = inject(AuthService);
  private dialog = inject(MatDialog);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private timelineService = inject(TimelineService);
  private yamcs = inject(YamcsService);

  view = input<TimelineView | null>();

  displayedColumns = ['name', 'description', 'tags', 'type', 'actions'];
  tableTrackerFn = (index: number, row: TimelineBand) => row.id;
  dataSource = new MatTableDataSource<TimelineBand>();

  constructor() {
    effect(() => {
      const view = this.view();
      this.dataSource.data = view?.bands || [];
    });
  }

  moveBandDown(index: number) {
    const bands = [...this.dataSource.data];
    if (index < bands.length - 1) {
      [bands[index], bands[index + 1]] = [bands[index + 1], bands[index]];
      this.updateView({ bands });
    }
  }

  moveBandUp(index: number) {
    const bands = [...this.dataSource.data];
    if (index > 0) {
      [bands[index], bands[index - 1]] = [bands[index - 1], bands[index]];
      this.updateView({ bands });
    }
  }

  openRenameViewDialog() {
    const view = this.view()!;
    this.dialog
      .open(RenameViewDialogComponent, {
        width: '400px',
        data: {
          name: view.name,
        },
      })
      .afterClosed()
      .subscribe((name) => {
        if (name) {
          this.updateView({ name });
        }
      });
  }

  async openCreateBandDialog() {
    const band = await this.timelineService.openCreateBandDialog({
      title: 'Create new',
      submitLabel: 'Add to view',
    });
    if (band) {
      const bands = [...this.dataSource.data, band];
      this.updateView({ bands });
    }
  }

  openAddBandDialog() {
    this.dialog
      .open(SelectBandDialogComponent, {
        width: '800px',
        panelClass: ['no-padding-dialog'],
      })
      .afterClosed()
      .subscribe((band) => {
        if (band) {
          const bands = [...this.dataSource.data, band];
          this.updateView({ bands });
        }
      });
  }

  openEditBandDialog(band: TimelineBand) {
    this.timelineService.openEditBandDialog(band);
  }

  removeFromView(band: TimelineBand) {
    const bands = this.dataSource.data.filter((x) => x.id !== band.id);
    this.updateView({ bands });
  }

  private updateView(options: { name?: string; bands?: TimelineBand[] }) {
    const view = this.view()!;
    const request: UpdateTimelineViewRequest = {
      name: options.name ?? view.name,
      bands: (options.bands ?? view.bands ?? []).map(
        (band: TimelineBand) => band.id,
      ),
    };
    return this.timelineService.updateView(view.id, request);
  }

  deleteView() {
    const view = this.view()!;
    if (confirm('Are you sure you want to delete the current view?')) {
      this.yamcs.yamcsClient
        .deleteTimelineView(this.yamcs.instance!, view.id)
        .then(() => {
          const url = `/timeline?c=${this.yamcs.context}`;
          this.router
            .navigateByUrl('/', { skipLocationChange: true })
            .then(() => this.router.navigateByUrl(url));
        })
        .catch((err) => this.messageService.showError(err));
    }
  }

  mayControlTimeline() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlTimeline');
  }
}
