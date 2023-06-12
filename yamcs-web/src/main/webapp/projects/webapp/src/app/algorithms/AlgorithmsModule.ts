import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AlgorithmDetail } from './AlgorithmDetail';
import { AlgorithmsRoutingModule, routingComponents } from './AlgorithmsRoutingModule';
import { AlgorithmStatusComponent } from './AlgorithmStatusComponent';

@NgModule({
  imports: [
    SharedModule,
    AlgorithmsRoutingModule,
  ],
  declarations: [
    routingComponents,
    AlgorithmDetail,
    AlgorithmStatusComponent,
  ],
})
export class AlgorithmsModule {
}
