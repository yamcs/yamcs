import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  viewChild,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import {
  Banner,
  ItemBand,
  LinePlot,
  StateBand,
  Timeline,
  TimeRuler,
} from '@fqqb/timeline';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { CreateBandWizardStepComponent } from '../create-band-wizard-step/create-band-wizard-step.component';

@Component({
  templateUrl: './create-band.component.html',
  styleUrl: './create-band.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CreateBandWizardStepComponent, WebappSdkModule],
})
export class CreateBandComponent implements AfterViewInit, OnDestroy {
  previewHeight = 30;

  timeRulerContainer =
    viewChild.required<ElementRef<HTMLDivElement>>('timeRuler');
  timeRulerPreview?: Timeline;

  itemBandContainer =
    viewChild.required<ElementRef<HTMLDivElement>>('itemBand');
  itemBandPreview?: Timeline;

  spacerContainer = viewChild.required<ElementRef<HTMLDivElement>>('spacer');
  spacerPreview?: Timeline;

  parameterPlotContainer =
    viewChild.required<ElementRef<HTMLDivElement>>('parameterPlot');
  parameterPlotPreview?: Timeline;

  parameterStatesContainer =
    viewChild.required<ElementRef<HTMLDivElement>>('parameterStates');
  parameterStatesPreview?: Timeline;

  commandBandContainer =
    viewChild.required<ElementRef<HTMLDivElement>>('commandBand');
  commandBandPreview?: Timeline;

  constructor(
    title: Title,
    readonly yamcs: YamcsService,
  ) {
    title.setTitle('Create a band');
  }

  ngAfterViewInit(): void {
    this.createTimeRulerPreview();
    this.createItemBandPreview();
    this.createSpacerPreview();
    this.createParameterPlotPreview();
    this.createParameterStatesPreview();
    this.createCommandBandPreview();
  }

  private createTimeRulerPreview() {
    const target = this.timeRulerContainer().nativeElement;
    target.style.height = `${this.previewHeight + 1}px`;
    this.timeRulerPreview = new Timeline(target);
    this.timeRulerPreview.sidebar = undefined;
    this.timeRulerPreview.tool = undefined;
    this.timeRulerPreview.background = 'transparent';

    const start = new Date();
    start.setUTCHours(0, 0, 0, 0);
    const stop = new Date(start.getTime());
    stop.setUTCMinutes(30);
    this.timeRulerPreview.setViewRange(start.getTime(), stop.getTime());

    const band = new TimeRuler(this.timeRulerPreview);
    band.timezone = 'UTC';
    band.contentHeight = this.previewHeight;
  }

  private createItemBandPreview() {
    const target = this.itemBandContainer().nativeElement;
    target.style.height = `${this.previewHeight + 1}px`;
    this.itemBandPreview = new Timeline(target);
    this.itemBandPreview.sidebar = undefined;
    this.itemBandPreview.tool = undefined;
    this.itemBandPreview.background = 'transparent';
    this.itemBandPreview.setViewRange(0, 100);

    const band = new ItemBand(this.itemBandPreview);
    band.itemHeight = this.previewHeight;
    band.paddingTop = 0;
    band.paddingBottom = 0;
    band.items = [
      { start: 10, stop: 30, label: 'A' },
      { start: 50, stop: 60, label: 'B' },
      { start: 80, stop: 95, label: 'C' },
    ];
  }

  private createSpacerPreview() {
    const target = this.spacerContainer().nativeElement;
    target.style.height = `${this.previewHeight + 1}px`;
    this.spacerPreview = new Timeline(target);
    this.spacerPreview.sidebar = undefined;
    this.spacerPreview.tool = undefined;
    this.spacerPreview.background = 'transparent';

    const band = new Banner(this.spacerPreview);
    band.text = '[empty space]';
    band.contentHeight = this.previewHeight;
    band.paddingTop = 0;
    band.paddingBottom = 0;
  }

  private createParameterPlotPreview() {
    const target = this.parameterPlotContainer().nativeElement;
    target.style.height = `${this.previewHeight + 1}px`;
    this.parameterPlotPreview = new Timeline(target);
    this.parameterPlotPreview.sidebar = undefined;
    this.parameterPlotPreview.tool = undefined;
    this.parameterPlotPreview.background = 'transparent';
    this.parameterPlotPreview.setViewRange(0, 50);

    const band = new LinePlot(this.parameterPlotPreview);
    band.contentHeight = this.previewHeight;
    band.paddingTop = 0;
    band.paddingBottom = 0;
    band.fill = 'lime';
    band.labelBackground = 'rgba(255, 255, 255, 0.75)';
    const points = new Map();
    for (let i = 0; i < 50; i += 0.01) {
      points.set(i, Math.sin(i));
    }
    band.lines = [
      {
        points,
        pointRadius: 0,
      },
    ];
  }

  private createParameterStatesPreview() {
    const target = this.parameterStatesContainer().nativeElement;
    target.style.height = `${this.previewHeight + 1}px`;
    this.parameterStatesPreview = new Timeline(target);
    this.parameterStatesPreview.sidebar = undefined;
    this.parameterStatesPreview.tool = undefined;
    this.parameterStatesPreview.background = 'transparent';
    this.parameterStatesPreview.setViewRange(0, 100);

    const band = new StateBand(this.parameterStatesPreview);
    band.contentHeight = this.previewHeight;
    band.paddingTop = 0;
    band.paddingBottom = 0;
    band.states = [
      { time: 0, label: 'ON', background: '#8dd3c7' },
      { time: 30, label: 'OFF', background: '#fdb462' },
      { time: 50, label: 'ON', background: '#8dd3c7' },
      { time: 80, label: 'OFF', background: '#fdb462' },
    ];
  }

  private createCommandBandPreview() {
    const target = this.commandBandContainer().nativeElement;
    target.style.height = `${this.previewHeight + 1}px`;
    this.commandBandPreview = new Timeline(target);
    this.commandBandPreview.sidebar = undefined;
    this.commandBandPreview.tool = undefined;
    this.commandBandPreview.background = 'transparent';
    this.commandBandPreview.setViewRange(0, 100);

    const band = new ItemBand(this.commandBandPreview);
    band.itemHeight = this.previewHeight;
    band.paddingTop = 0;
    band.paddingBottom = 0;
    band.items = [
      { start: 10, label: 'A' },
      { start: 50, label: 'B' },
      { start: 80, label: 'C' },
    ];
  }

  ngOnDestroy(): void {
    this.timeRulerPreview?.disconnect();
    this.itemBandPreview?.disconnect();
    this.spacerPreview?.disconnect();
    this.parameterPlotPreview?.disconnect();
    this.parameterStatesPreview?.disconnect();
    this.commandBandPreview?.disconnect();
  }
}
