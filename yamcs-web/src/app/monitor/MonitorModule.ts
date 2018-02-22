import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { MonitorRoutingModule, routingComponents } from './MonitorRoutingModule';
import { MonitorPageTemplate } from './template/MonitorPageTemplate';
import { ProcessorInfoComponent } from './template/ProcessorInfoComponent';
import { SeverityComponent } from './events/SeverityComponent';
import { DisplayNavigator } from './displays/DisplayNavigator';
import { SaveLayoutDialog } from './displays/SaveLayoutDialog';
import { LayoutComponent } from './displays/LayoutComponent';

const dialogComponents = [
  SaveLayoutDialog,
];

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    DisplayNavigator,
    LayoutComponent,
    MonitorPageTemplate,
    ProcessorInfoComponent,
    SeverityComponent,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class MonitorModule {
}
