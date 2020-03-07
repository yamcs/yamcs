import { ChangeDetectionStrategy, ChangeDetectorRef, Component, forwardRef, Input, OnChanges } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { Command, CommandsPage, GetCommandsOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-command-selector',
  templateUrl: './CommandSelector.html',
  styleUrls: ['./CommandSelector.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CommandSelector),
      multi: true
    }
  ]
})
export class CommandSelector implements ControlValueAccessor, OnChanges {

  @Input()
  instance: string;

  @Input()
  path: string;

  displayedColumns = ['name', 'description', 'significance'];
  dataSource = new MatTableDataSource<BrowseItem>([]);

  currentSystem$ = new BehaviorSubject<string>('/');
  selectedCommand$ = new BehaviorSubject<BrowseItem | null>(null);

  private onChange = (_: Command | null) => { };
  private onTouched = () => { };

  constructor(private yamcs: YamcsService, private changeDetection: ChangeDetectorRef) {
    this.selectedCommand$.subscribe(item => {
      if (item && item.command) {
        return this.onChange(item.command);
      } else {
        return this.onChange(null);
      }
    });
  }

  ngOnChanges() {
    if (this.instance) {
      this.loadCurrentSystem('/');
    }
  }

  private loadCurrentSystem(system: string) {
    const options: GetCommandsOptions = {
      noAbstract: true,
    };
    if (system) {
      options.system = system;
    }

    this.yamcs.yamcsClient.getCommands(this.instance, options).then(page => {
      this.changeSystem(page);
      this.currentSystem$.next(system);
    });
  }

  private changeSystem(page: CommandsPage) {
    this.selectedCommand$.next(null);
    const items: BrowseItem[] = [];
    for (const spaceSystem of page.spaceSystems || []) {
      items.push({
        folder: true,
        name: spaceSystem,
      });
    }
    for (const command of page.commands || []) {
      items.push({
        folder: false,
        name: command.name,
        command: command,
      });
    }
    this.dataSource.data = items;
    this.changeDetection.detectChanges();
  }

  selectRow(row: BrowseItem) {
    if (row.folder) {
      this.selectedCommand$.next(null);
      this.loadCurrentSystem(row.name);
    } else {
      this.selectedCommand$.next(row);
    }
  }

  selectParent() {
    const currentPrefix = this.currentSystem$.value;
    const idx = currentPrefix.lastIndexOf('/');
    if (idx !== -1) {
      const parentPrefix = currentPrefix.substring(0, idx) || '/';
      this.selectedCommand$.next(null);
      this.loadCurrentSystem(parentPrefix);
    }
  }

  writeValue(value: any) {
    this.path = value;
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }
}

export class BrowseItem {
  folder: boolean;
  name: string;
  command?: Command;
}
