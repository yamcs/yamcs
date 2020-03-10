import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Parameter } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './ParameterPage.html',
  styleUrls: ['./ParameterPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterPage {

  instance: string;
  parameter$ = new BehaviorSubject<Parameter | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
  ) {
    this.instance = yamcs.getInstance();

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeParameter(qualifiedName);
    });
  }

  changeParameter(qualifiedName: string) {
    this.yamcs.yamcsClient.getParameter(this.instance, qualifiedName).then(parameter => {
      this.parameter$.next(parameter);
      this.title.setTitle(parameter.name);
    });
  }
}
