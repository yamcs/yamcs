import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Parameter } from '@yamcs/client';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ParameterCalibrationTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterCalibrationTab {

  parameter$: Promise<Parameter>;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.parameter$ = yamcs.getSelectedInstance().getParameter(qualifiedName);
  }
}
