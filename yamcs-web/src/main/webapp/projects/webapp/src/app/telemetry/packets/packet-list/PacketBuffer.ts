import { Packet } from '@yamcs/webapp-sdk';

export class PacketBuffer {

  public dirty = false;

  private archivePackets: Packet[] = [];

  addArchiveData(packets: Packet[]) {
    this.archivePackets = this.archivePackets.concat(packets);
    this.dirty = true;
  }

  reset() {
    this.archivePackets = [];
    this.dirty = true;
  }

  snapshot(): Packet[] {
    return this.archivePackets;
  }
}
