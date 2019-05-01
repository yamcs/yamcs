import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CommandCompletion } from './command-history/CommandCompletion';
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
    CommandCompletion,
    CommandQueuesTable,
    QueuedCommandsTable,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class CommandingModule {
}
