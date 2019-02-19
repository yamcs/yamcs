import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CommandQueuesTable } from './command-queues/CommandQueuesTable';
import { QueuedCommandsTable } from './command-queues/QueuedCommandsTable';
import { AddStackedCommandDialog } from './command-stack/AddStackedCommandDialog';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';

const dialogComponents = [
  AddStackedCommandDialog,
];

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    CommandQueuesTable,
    QueuedCommandsTable,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class CommandingModule {
}
