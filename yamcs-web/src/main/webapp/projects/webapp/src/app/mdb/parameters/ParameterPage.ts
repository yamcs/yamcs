import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Parameter, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';


@Component({
  templateUrl: './ParameterPage.html',
  styleUrls: ['./ParameterPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterPage {

  parameter$ = new BehaviorSubject<Parameter | null>(null);
  offset$ = new BehaviorSubject<string | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private title: Title,
  ) {
    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeParameter(qualifiedName);
    });
  }

  changeParameter(qualifiedName: string) {
    this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, qualifiedName).then(parameter => {
      this.parameter$.next(parameter);

      let offset;
      if (qualifiedName !== parameter.qualifiedName) {
        offset = qualifiedName.substring(parameter.qualifiedName.length);
      } else {
        offset = null;
      }
      this.offset$.next(offset);

      this.title.setTitle(parameter.name + (offset || ''));
    });
  }
}
