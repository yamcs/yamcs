import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { Title } from '@angular/platform-browser';
import { Argument, ArgumentAssignment, Command, GetCommandsOptions, IssueCommandResponse } from '@yamcs/client';
import { BehaviorSubject, fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandsDataSource } from '../../mdb/commands/CommandsDataSource';

@Component({
  templateUrl: './SendCommandPage.html',
  styleUrls: ['./SendCommandPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SendCommandPage implements AfterViewInit {

  pageSize = 100;

  @ViewChild(MatPaginator, { static: false })
  paginator: MatPaginator;

  @ViewChild('filter', { static: false })
  filter: ElementRef;

  step$ = new BehaviorSubject<number>(1);

  dataSource: CommandsDataSource;
  selectedCommand$ = new BehaviorSubject<Command | null>(null);
  displayedColumns = [
    'name',
    'significance',
    'shortDescription',
  ];

  commandConfigurationForm = new FormGroup({
    _comment: new FormControl(),
  });
  arguments: Argument[] = [];
  initialValueCount = 0;
  showAll$ = new BehaviorSubject<boolean>(false);

  issueCommandResponse$ = new BehaviorSubject<IssueCommandResponse | null>(null);

  constructor(
    title: Title,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    title.setTitle('Send a command');
    this.dataSource = new CommandsDataSource(yamcs);
  }

  ngAfterViewInit() {
    this.updateDataSource();
    this.paginator.page.subscribe(() => {
      this.updateDataSource();
    });

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(400),
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
      distinctUntilChanged(),
    ).subscribe(() => {
      this.paginator.pageIndex = 0;
      this.updateDataSource();
    });
  }

  private updateDataSource() {
    const options: GetCommandsOptions = {
      noAbstract: true,
      pos: this.paginator.pageIndex * this.pageSize,
      limit: this.pageSize,
    };
    const filterValue = this.filter.nativeElement.value.trim().toLowerCase();
    if (filterValue) {
      options.q = filterValue;
    }
    this.dataSource.loadCommands(options).then(() => {
      this.selectedCommand$.next(null);
    });
  }

  goToPage1() {
    this.selectedCommand$.next(null);
    this.filter.nativeElement.value = '';
    this.paginator.pageIndex = 0;
    this.updateDataSource();
    this.step$.next(1);
  }

  goToConfigureCommand(selectedCommand: Command) {
    this.selectedCommand$.next(selectedCommand);
    this.step$.next(2);

    // Get command detail
    const qname = this.selectedCommand$.value!.qualifiedName;
    this.yamcs.getInstanceClient()!.getCommand(qname).then(command => {

      // Reset previous state (if any)
      this.commandConfigurationForm.reset();
      for (const controlName in this.commandConfigurationForm.controls) {
        if (this.commandConfigurationForm.hasOwnProperty(controlName)) {
          if (controlName !== '_comment') {
            this.commandConfigurationForm.removeControl(controlName);
          }
        }
      }
      this.arguments = [];
      this.initialValueCount = 0;
      this.showAll$.next(false);

      // Order command definitions top-down
      const commandHierarchy: Command[] = [];
      let c: Command | undefined = command;
      while (c) {
        commandHierarchy.unshift(c);
        c = c.baseCommand;
      }

      const assignments = new Map<string, string>();
      for (const c of commandHierarchy) {
        if (c.argument) {
          for (const argument of c.argument) {
            this.arguments.push(argument);
          }
        }
        if (c.argumentAssignment) {
          for (const assignment of c.argumentAssignment) {
            assignments.set(assignment.name, assignment.value);
          }
        }
      }

      // Assignments cannot be overriden by user, so filter them out
      this.arguments = this.arguments.filter(argument => !assignments.has(argument.name));

      for (const arg of this.arguments) {
        let value = arg.initialValue;
        if (value === undefined) {
          value = '';
        } else {
          this.initialValueCount++;
        }
        this.commandConfigurationForm.addControl(
          arg.name, new FormControl(value, Validators.required));
      }
    });
  }

  sendCommand() {
    const assignments: ArgumentAssignment[] = [];
    let comment = null;
    for (const controlName in this.commandConfigurationForm.controls) {
      if (this.commandConfigurationForm.controls.hasOwnProperty(controlName)) {
        if (controlName === '_comment') {
          comment = this.commandConfigurationForm.value['_comment'];
        } else {
          const control = this.commandConfigurationForm.controls[controlName];
          if (!this.isArgumentWithInitialValue(controlName) || control.touched) {
            assignments.push({ name: controlName, value: this.commandConfigurationForm.value[controlName] });
          }
        }
      }
    }

    const processor = this.yamcs.getProcessor();
    const qname = this.selectedCommand$.value!.qualifiedName;
    this.yamcs.getInstanceClient()!.issueCommand(processor.name, qname, {
      assignment: assignments,
      comment: comment || undefined,
    }).then(response => {
      this.issueCommandResponse$.next(response);
      this.step$.next(3);
    }).catch(err => {
      this.messageService.showError(err);
    });
  }

  private isArgumentWithInitialValue(argumentName: string) {
    for (const arg of this.arguments) {
      if (arg.name === argumentName) {
        return arg.initialValue !== undefined;
      }
    }
    return false;
  }
}
