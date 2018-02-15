import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { MonitorRoutingModule, routingComponents } from './MonitorRoutingModule';
import { MonitorPageTemplate } from './components/MonitorPageTemplate';
import { ProcessorInfoComponent } from './components/ProcessorInfoComponent';
import { SeverityComponent } from './components/SeverityComponent';

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    MonitorPageTemplate,
    ProcessorInfoComponent,
    SeverityComponent,
  ]
})
export class MonitorModule {
}
