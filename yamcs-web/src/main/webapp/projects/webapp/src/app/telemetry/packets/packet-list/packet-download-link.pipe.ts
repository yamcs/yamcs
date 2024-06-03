import { Pipe, PipeTransform } from '@angular/core';
import { Packet, YamcsService } from '@yamcs/webapp-sdk';

@Pipe({
  standalone: true,
  name: 'packetDownloadLink',
})
export class PacketDownloadLinkPipe implements PipeTransform {

  constructor(private yamcs: YamcsService) {
  }

  transform(packet: Packet | null): string | null {
    if (!packet) {
      return null;
    }

    const instance = this.yamcs.instance!;
    return this.yamcs.yamcsClient.getPacketDownloadURL(
      instance, packet.id.name, packet.generationTime, packet.sequenceNumber);
  }
}
