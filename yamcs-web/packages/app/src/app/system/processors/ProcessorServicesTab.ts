import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { Service } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './ProcessorServicesTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProcessorServicesTab {

  dataSource = new MatTableDataSource<Service>();

  constructor(route: ActivatedRoute, private yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('name')!;

    yamcs.getInstanceClient()!.getProcessor(name).then(processor => {
      this.dataSource.data = processor.service || [];
    });
  }
}
