import { Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-warning-message',
  templateUrl: './warning-message.component.html',
  styleUrl: './warning-message.component.css',
  imports: [
    MatIcon,
  ],
})
export class YaWarningMessage {
}
