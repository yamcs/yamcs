import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-main-page',
  templateUrl: './MainPage.html',
  styleUrls: ['./MainPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainPage {

}
