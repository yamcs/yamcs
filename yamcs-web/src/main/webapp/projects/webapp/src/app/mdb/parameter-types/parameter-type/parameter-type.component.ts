import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import {
  ParameterType,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { ParameterTypeDetailComponent } from '../parameter-type-detail/parameter-type-detail.component';

@Component({
  templateUrl: './parameter-type.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ParameterTypeDetailComponent, WebappSdkModule],
})
export class ParameterTypeComponent {
  ptype$ = new BehaviorSubject<ParameterType | null>(null);

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private title: Title,
  ) {
    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe((params) => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeParameterType(qualifiedName);
    });
  }

  changeParameterType(qualifiedName: string) {
    this.yamcs.yamcsClient
      .getParameterType(this.yamcs.instance!, qualifiedName)
      .then((ptype) => {
        this.ptype$.next(ptype);
        this.title.setTitle(ptype.name);
      });
  }
}
