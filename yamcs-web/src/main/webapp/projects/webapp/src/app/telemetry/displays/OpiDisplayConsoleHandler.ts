import { ConsoleHandler } from '@yamcs/opi';
import { MessageService } from '../../core/services/MessageService';

export class OpiDisplayConsoleHandler implements ConsoleHandler {

  constructor(private messageService: MessageService) {
  }

  writeInfo(message: string) {
    this.messageService.showInfo(message);
  }

  writeError(message: string) {
    this.messageService.showError(message);
  }

  writeWarning(message: string) {
    this.messageService.showInfo(message);
  }
}
