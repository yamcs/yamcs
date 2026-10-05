import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import {
  ArrayInfo,
  Parameter,
  ParameterDimension,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-parameter-array-info',
  templateUrl: './parameter-array-info.component.html',
  imports: [WebappSdkModule],
})
export class ParameterArrayInfoComponent {
  @Input({ required: true })
  arrayInfo: ArrayInfo;

  constructor(
    readonly yamcs: YamcsService,
    private router: Router,
  ) {}

  isDynamic(dimension: ParameterDimension) {
    return (
      dimension.parameter !== undefined ||
      dimension.aggregateMember !== undefined
    );
  }

  getParameterRoute(parameter: Parameter) {
    if (this.router.url.startsWith('/mdb/')) {
      return ['/mdb/parameters/', parameter.qualifiedName];
    } else {
      return ['/telemetry/parameters' + parameter.qualifiedName];
    }
  }
}
