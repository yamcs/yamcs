import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { InstanceTemplate } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './CreateInstancePage1.html',
  styleUrls: ['./CreateInstancePage1.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateInstancePage1 {

  dataSource = new MatTableDataSource<InstanceTemplate>([]);
  selection = new SelectionModel<Element>(false, []);

  selectedTemplate$ = new BehaviorSubject<InstanceTemplate | null>(null);

  displayedColumns = [
    'select',
    'name',
  ];

  constructor(private yamcs: YamcsService, title: Title, private router: Router) {
    title.setTitle('Create an Instance - Yamcs');
    this.yamcs.yamcsClient.getInstanceTemplates().then(templates => {
      this.dataSource.data = templates;
    });
  }

  selectRow(row: Element) {
    this.selection.toggle(row);
    if (this.selection.isSelected(row)) {
      this.selectedTemplate$.next(row as any);
    } else {
      this.selectedTemplate$.next(null);
    }
  }

  goToCustomizeInstance() {
    const template = this.selectedTemplate$.value;
    if (template) {
      this.router.navigateByUrl('/create-instance/' + template.name);
    }
  }
}
