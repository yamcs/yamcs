import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { RocksDbRoutingModule, routingComponents } from './RocksDbRoutingModule';

@NgModule({
  imports: [
    SharedModule,
    RocksDbRoutingModule,
  ],
  declarations: [
    routingComponents,
  ],
})
export class RocksDbModule {
}
