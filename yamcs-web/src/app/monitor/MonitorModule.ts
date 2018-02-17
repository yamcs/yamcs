import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { MonitorRoutingModule, routingComponents } from './MonitorRoutingModule';
import { MonitorPageTemplate } from './template/MonitorPageTemplate';
import { ProcessorInfoComponent } from './template/ProcessorInfoComponent';
import { SeverityComponent } from './events/SeverityComponent';
import { DisplayNavigator } from './displays/DisplayNavigator';

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    DisplayNavigator,
    MonitorPageTemplate,
    ProcessorInfoComponent,
    SeverityComponent,
  ]
})
export class MonitorModule {
}
