import { Component } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';

@Component({
  standalone: true,
  selector: 'ya-detail-toolbar',
  templateUrl: './detail-toolbar.component.html',
  styleUrl: './detail-toolbar.component.css',
  imports: [
    MatToolbar,
  ],
})
export class YaDetailToolbar {
}
