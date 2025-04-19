import {
  ChangeDetectionStrategy,
  Component,
  input,
  Input,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import {
  GetParameterArchivePidsOptions,
  MessageService,
  ParameterId,
  ParseFilterSubscription,
  WebappSdkModule,
  YamcsService,
  YaSearchFilter2,
} from '@yamcs/webapp-sdk';
import { PID_COMPLETIONS } from './completions';

@Component({
  templateUrl: './parameter-archive.component.html',
  styleUrl: './parameter-archive.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ParameterArchiveComponent implements OnInit, OnDestroy {
  @Input()
  database: string;

  // From resolver
  parseFilterSubscription = input.required<ParseFilterSubscription>();

  searchFilter = viewChild.required<YaSearchFilter2>('searchFilter');
  completions = PID_COMPLETIONS;

  displayedColumns = [
    'pid',
    'parameter',
    'rawType',
    'engType',
    'gids',
    'actions',
  ];

  filterForm = new FormGroup({
    filter: new FormControl<string | null>(null),
  });

  dataSource = new MatTableDataSource<ParameterId>();
  continuationToken = signal<string | undefined>(undefined);

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
  ) {}

  ngOnInit(): void {
    this.parseFilterSubscription().addMessageListener((data) => {
      if (data.errorMessage) {
        this.searchFilter().addErrorMark(data.errorMessage, {
          beginLine: data.beginLine!,
          beginColumn: data.beginColumn!,
          endLine: data.endLine!,
          endColumn: data.endColumn!,
        });
      } else {
        this.searchFilter().clearErrorMark();
      }
    });

    this.loadData();

    this.filterForm.get('filter')!.valueChanges.forEach(() => {
      this.loadData();
    });
  }

  loadData(next?: string) {
    const { controls } = this.filterForm;
    const options: GetParameterArchivePidsOptions = {
      limit: 200,
      next,
    };
    const filter = controls['filter'].value;
    if (filter) {
      options.filter = filter;
    }

    this.yamcs.yamcsClient
      .getParameterArchivePids(this.database, options)
      .then((page) => {
        if (next) {
          this.dataSource.data = [
            ...this.dataSource.data,
            ...(page.pids || []),
          ];
        } else {
          this.dataSource.data = page.pids || [];
        }
        this.continuationToken.set(page.continuationToken);
      })
      .catch((err) => this.messageService.showError(err));
  }

  parseQuery(typedQuery: string) {
    this.parseFilterSubscription().sendMessage({
      resource: 'pids',
      filter: typedQuery,
    });
  }

  ngOnDestroy(): void {
    this.parseFilterSubscription().cancel();
  }
}
