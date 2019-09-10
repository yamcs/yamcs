import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { Command } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AddCommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddCommandPage {

  dataSource = new MatTableDataSource<Command>([]);
  selection = new SelectionModel<Element>(false, []);

  selectedCommand$ = new BehaviorSubject<Command | null>(null);

  displayedColumns = [
    'select',
    'name',
  ];

  constructor(private yamcs: YamcsService, title: Title, private router: Router) {
    title.setTitle('Add a Command');
    this.yamcs.getInstanceClient()!.getCommands().then(page => {
      this.dataSource.data = page.commands || [];
    });
  }

  selectRow(row: Element) {
    this.selection.toggle(row);
    if (this.selection.isSelected(row)) {
      this.selectedCommand$.next(row as any);
    } else {
      this.selectedCommand$.next(null);
    }
  }

  goToCustomizeInstance() {
    const template = this.selectedCommand$.value;
    if (template) {
      this.router.navigateByUrl('/create-instance/' + template.name);
    }
  }
}
