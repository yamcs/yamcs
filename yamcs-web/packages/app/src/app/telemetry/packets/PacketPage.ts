import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance, Packet } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './PacketPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PacketPage {


  displayedColumns = [
    'generationTime',
    'receptionTime',
    'data',
    'size',
  ];

  filterControl = new FormControl();

  instance: Instance;
  packetName: string;
  dataSource = new MatTableDataSource<Packet>();

  constructor(
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private router: Router,
    title: Title) {
    this.instance = yamcs.getInstance();
    this.packetName = route.snapshot.paramMap.get('qualifiedName')!;
    title.setTitle(this.packetName);

    this.yamcs.getInstanceClient()!.getPackets({ name: this.packetName }).then(page => {
      this.dataSource.data = page.packet || [];
    });
  }

  refreshView() {
    this.yamcs.getInstanceClient()!.getPackets({ name: this.packetName }).then(page => {
      this.dataSource.data = page.packet || [];
    });
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
