import { ComponentFactory, Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { Printable } from '../../shared/print/Printable';

export class PrintOrder {
  factory: ComponentFactory<Printable>;
  title: string;
  data: any;
}

@Injectable({
  providedIn: 'root',
})
export class PrintService {

  printOrders$ = new Subject<PrintOrder>();

  printComponent(factory: ComponentFactory<Printable>, title: string, data: any) {
    this.printOrders$.next({ factory, title, data });
  }
}
