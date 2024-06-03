import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { MessageService, Parameter, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../shared/instance-toolbar/instance-toolbar.component';

interface Result {
  resources: Resource[];
  totalSize: number;
  continuationToken?: string;
}

interface Resource {
  label: string;
  type: string;
  link: string[];
}

@Component({
  standalone: true,
  templateUrl: './search.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class SearchComponent {

  term$ = new BehaviorSubject<string | null>(null);
  result$ = new BehaviorSubject<Result | null>(null);

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    route: ActivatedRoute,
    private messageService: MessageService,
  ) {
    title.setTitle("Search");

    route.queryParamMap.subscribe(snapshot => {
      const q = snapshot.get('q') ?? '';
      this.fetchPage(q).then(page => {
        this.term$.next(q);
        this.result$.next({
          resources: this.toResources(page.parameters || []),
          totalSize: page.totalSize,
          continuationToken: page.continuationToken,
        });
      });
    });
  }

  loadMoreData() {
    const q = this.term$.value ?? '';
    const continuationToken = this.result$.value?.continuationToken;
    this.fetchPage(q, continuationToken).then(page => {
      const result = this.result$.value!;
      result.resources = [
        ...result.resources,
        ...this.toResources(page.parameters || []),
      ];
      this.result$.next({
        ...result,
        continuationToken: page.continuationToken,
      });
    });
  }

  private toResources(parameters: Parameter[]): Resource[] {
    return parameters.map(p => {
      return {
        label: p.qualifiedName,
        type: 'Parameter',
        link: ['/telemetry/parameters' + p.qualifiedName],
      };
    });
  }

  private fetchPage(q: string, continuationToken?: string) {
    const promise = this.yamcs.yamcsClient.getParameters(this.yamcs.instance!, {
      q,
      next: continuationToken,
      limit: 50,
      searchMembers: true,
    });
    promise.catch(err => this.messageService.showError(err));
    return promise;
  }
}
