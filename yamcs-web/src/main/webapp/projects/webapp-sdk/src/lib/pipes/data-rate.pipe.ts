import { Pipe, PipeTransform } from '@angular/core';

const sizes = ['bps', 'kbps', 'Mbps', 'Gbps', 'Tbps'];

@Pipe({
  standalone: true,
  name: 'dataRate',
})
export class DataRatePipe implements PipeTransform {

  transform(bps: string | number | null, decimals = 1): string | null {
    const bpsNumber = Number(bps);
    if (bpsNumber === 0) {
      return '0 bps';
    } else if (!bps) {
      return null;
    }
    const i = Math.floor(Math.log(bpsNumber) / Math.log(1000));
    return parseFloat((bpsNumber / Math.pow(1000, i)).toFixed(decimals)) + ' ' + sizes[i];
  }
}
