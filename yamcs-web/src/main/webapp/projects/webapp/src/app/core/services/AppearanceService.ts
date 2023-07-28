import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AppearanceService {

  public zenMode$ = new BehaviorSubject<boolean>(false);
}
