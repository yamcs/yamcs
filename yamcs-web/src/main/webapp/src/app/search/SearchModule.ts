import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { routingComponents, SearchRoutingModule } from './SearchRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    SearchRoutingModule,
  ],
  declarations: [
    routingComponents,
  ],
})
export class SearchModule {
}
