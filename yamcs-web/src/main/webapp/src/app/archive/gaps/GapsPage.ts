import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Gap } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { RequestPlaybackDialog } from './RequestPlaybackDialog';

@Component({
  templateUrl: './GapsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GapsPage {

  displayedColumns = [
    'select',
    'apid',
    'start',
    'stop',
    'duration',
    'startSequence',
    'stopSequence',
    'packetCount',
  ];

  dataSource = new MatTableDataSource<Gap>();
  selection = new SelectionModel<Gap>(true, []);

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private dialog: MatDialog,
    private messageService: MessageService,
  ) {
    title.setTitle('Gaps');
    yamcs.yamcsClient.getGaps(yamcs.getInstance().name).then(page => {
      this.dataSource.data = page.gaps || [];
    });
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.data.forEach(row => {
        if (row.start && row.stop) {
          this.selection.select(row);
        }
      });
  }

  toggleOne(row: Gap) {
    if (!row.start || !row.stop) {
      return;
    }
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  openPlaybackDialog() {
    const dialogRef = this.dialog.open(RequestPlaybackDialog, {
      data: {
        'gaps': this.selection.selected,
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.yamcs.yamcsClient.requestPlayback(this.yamcs.getInstance().name, result.link, {
          ranges: result.ranges,
        }).then(() => this.messageService.showInfo('Playback requested')).catch(err => this.messageService.showError(err));
      }
    });
  }

  refreshView() {
    this.yamcs.yamcsClient.getGaps(this.yamcs.getInstance().name).then(page => {
      this.selection.clear();
      this.dataSource.data = page.gaps || [];
    });
  }
}
