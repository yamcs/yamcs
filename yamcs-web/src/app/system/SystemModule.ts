import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { SystemRoutingModule, routingComponents } from './SystemRoutingModule';
import { SystemToolbar } from './components/SystemToolbar';
import { SystemPageTemplate } from './components/SystemPageTemplate';
import { RecordComponent } from './components/RecordComponent';
import { HexComponent } from './components/HexComponent';

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
    HexComponent,
    RecordComponent,
    SystemPageTemplate,
    SystemToolbar,
  ]
})
export class SystemModule {
}
