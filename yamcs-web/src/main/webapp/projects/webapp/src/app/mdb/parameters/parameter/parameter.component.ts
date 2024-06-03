import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Parameter, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { ParameterDetailComponent } from '../parameter-detail/parameter-detail.component';

@Component({
  standalone: true,
  templateUrl: './parameter.component.html',
  styleUrl: './parameter.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    ParameterDetailComponent,
    WebappSdkModule,
  ],
})
export class ParameterComponent {

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
