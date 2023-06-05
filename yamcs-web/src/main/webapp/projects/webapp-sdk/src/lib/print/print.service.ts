import { Injectable, Type } from '@angular/core';
import { Subject } from 'rxjs';
import { Printable } from './Printable';

export class PrintOrder {
  componentType: Type<Printable>;
  title: string;
  data: any;
}

@Injectable({
  providedIn: 'root',
})
export class PrintService {

  printOrders$ = new Subject<PrintOrder>();

  printComponent(componentType: Type<Printable>, title: string, data: any) {
    this.printOrders$.next({ componentType, title, data });
  }
}
