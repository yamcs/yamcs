import {
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  ElementRef,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Link, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { FormGroup, FormControl, Validators } from '@angular/forms';

interface SdlsInfo {
  seq: bigint;
  keyLengthBits: number;
  algorithm: string;
  sdlsHeaderSize: number;
  sdlsTrailerSize: number;
  sdlsOverhead: number;
}

interface PageData {
  link: Link;
  sdlsInfo: SdlsInfo;
  spi: number;
}

function bigEndianByteStringToBigInt(bytes: string): bigint {
  let decodedBytes = atob(bytes);
  let value = 0n;
  for (const byte of decodedBytes) {
    value = (value << 8n) | BigInt(byte.charCodeAt(0));
  }
  return value;
}

function bigIntToBigEndianByteString(value: bigint): string {
  // Special case: 0 → single zero byte
  if (value === 0n) {
    return btoa('\x00');
  }

  let result = '';
  while (value > 0n) {
    const byte = Number(value & 0xffn);
    result = String.fromCharCode(byte) + result;
    value >>= 8n;
  }

  return btoa(result);
}

@Component({
  templateUrl: './sdls-sa.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class SdlsSaComponent {
  @ViewChild('uploader')
  private uploaderEl: ElementRef<HTMLInputElement>;

  data$ = new BehaviorSubject<PageData | null>(null);
  seqNumForm = new FormGroup({
    seqNum: new FormControl(0, Validators.required),
  });
  secretKeyForm = new FormGroup({
    secretKey: new FormControl(null, Validators.required),
  });

  constructor(
    route: ActivatedRoute,
    readonly yamcs: YamcsService,
  ) {
    route.paramMap.subscribe((params) => {
      const linkName = params.get('link')!;
      const spi = parseInt(params.get('spi')!);
      this.updateData(linkName, spi);
    });
  }

  private updateData(linkName: string, spi: number) {
    const instance = this.yamcs.instance!;
    let linkPromise = this.yamcs.yamcsClient.getLink(instance, linkName);
    let saPromise = this.yamcs.yamcsClient.getSdlsSa(instance, linkName, spi);
    Promise.all([linkPromise, saPromise]).then(([link, sa]) => {
      this.data$.next({
        spi: spi,
        link: link,
        sdlsInfo: {
          seq: bigEndianByteStringToBigInt(sa.seq),
          algorithm: sa.algorithm,
          keyLengthBits: sa.keyLen,
          sdlsHeaderSize: sa.sdlsHeaderSize,
          sdlsTrailerSize: sa.sdlsTrailerSize,
          sdlsOverhead: sa.sdlsOverhead,
        },
      });
    });
  }

  resetSeqNum(link: string, spi: number) {
    const instance = this.yamcs.instance!;
    const value = this.seqNumForm.value.seqNum!;
    this.yamcs.yamcsClient
      .setSdlsSeqCtr(
        instance,
        link,
        spi,
        bigIntToBigEndianByteString(BigInt(value)),
      )
      .then(() => {
        this.updateData(link, spi);
      });
  }

  updateSecretKey(link: string, spi: number) {
    const instance = this.yamcs.instance!;
    let file = this.uploaderEl.nativeElement.files![0];
    console.log('updating key');
    this.yamcs.yamcsClient.setSdlsKey(instance, link, spi, file).then(() => {
      console.log('updated');
      this.updateData(link, spi);
    });
  }
}
