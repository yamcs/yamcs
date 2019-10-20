import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { QueuesTable } from './queues/QueuesTable';

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    AcknowledgmentIcon,
    QueuedCommandsTable,
    QueuesTable,
    SendCommandWizardStep,
  ],
})
export class CommandingModule {
}
