import { Ellipse, G, LinearGradient, Polygon, Stop } from '../../tags';
import { Bounds } from '../Bounds';
import { Color } from '../Color';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

export class BooleanSwitch extends AbstractWidget {

  private effect3d: boolean;
  private onColor: Color;
  private onLabel: string;
  private offColor: Color;
  private offLabel: string;

  constructor(node: Element, display: OpiDisplay, absoluteX: number, absoluteY: number) {
    super(node, display, absoluteX, absoluteY);
    this.effect3d = utils.parseBooleanChild(this.node, 'effect_3d');
    const onColorNode = utils.findChild(this.node, 'on_color');
    this.onColor = utils.parseColorChild(onColorNode);
    this.onLabel = utils.parseStringChild(this.node, 'on_label');
    const offColorNode = utils.findChild(this.node, 'off_color');
    this.offColor = utils.parseColorChild(offColorNode);
    this.offLabel = utils.parseStringChild(this.node, 'off_label');
  }

  draw(g: G) {
    if (this.width > this.height) {
      this.drawHorizontal(g);
    } else {
      this.drawVertical(g);
    }
  }

  private drawHorizontal(g: G) {
    let areaWidth = this.width;
    let areaHeight = this.height;
    if (areaHeight > areaWidth / 2) {
      areaHeight = Math.floor(this.width / 2);
    } else {
      areaWidth = Math.floor(2 * this.height);
    }

    const pedBounds = {
      x: Math.floor((63.0 / 218.0) * areaWidth),
      y: 0,
      width: areaHeight / 2,
      height: areaHeight / 2,
    };
    this.drawPedestal(g, pedBounds);

    const largeWidth = Math.floor((35.0 / 218.0) * areaWidth);
    const largeHeight = Math.floor((45.0 / 105.0) * areaHeight);
    const smallWidth = Math.floor((43.0 / 218.0) * areaWidth);
    const smallHeight = Math.floor((35.0 / 105.0) * areaHeight);

    const onLargeBounds: Bounds = {
      x: 2 * pedBounds.x + pedBounds.width - largeWidth,
      y: pedBounds.height / 2 - largeHeight / 2,
      width: largeWidth,
      height: largeHeight
    };
    const offLargeBounds: Bounds = {
      x: 0,
      y: pedBounds.height / 2 - largeHeight / 2,
      width: largeWidth,
      height: largeHeight,
    };
    const smallMove = Math.floor((1.0 / 7.0) * pedBounds.width);
    const onSmallBounds: Bounds = {
      x: pedBounds.x + pedBounds.width / 2 - smallWidth / 2 + smallMove,
      y: pedBounds.y + pedBounds.height / 2 - smallHeight / 2,
      width: smallWidth,
      height: smallHeight,
    };
    const offSmallBounds: Bounds = {
      x: pedBounds.x + pedBounds.width / 2 - smallWidth / 2 - smallMove,
      y: pedBounds.y + pedBounds.height / 2 - smallHeight / 2,
      width: smallWidth,
      height: smallHeight,
    };
    this.drawHorizontalBar(g, offSmallBounds, offLargeBounds, false);
    this.drawHorizontalBar(g, onSmallBounds, onLargeBounds, true);
  }

  private drawVertical(g: G) {
    let areaWidth = this.width;
    let areaHeight = this.height;
    if (areaWidth > areaHeight / 2) {
        areaWidth = Math.floor(this.height / 2);
    } else {
        areaHeight = Math.floor(2 * this.width);
    }

    const pedBounds = {
      x: 0,
      y: Math.floor((63.0 / 218.0) * areaHeight),
      width: areaWidth / 2,
      height: areaWidth / 2,
    };
    this.drawPedestal(g, pedBounds);

    const largeWidth = Math.floor((45.0 / 105.0) * areaWidth);
    const largeHeight = Math.floor((35.0 / 218.0) * areaHeight);
    const smallWidth = Math.floor((35.0 / 105.0) * areaWidth);
    const smallHeight = Math.floor((43.0 / 218.0) * areaHeight);

    const onLargeBounds: Bounds = {
      x: pedBounds.width / 2 - largeWidth / 2,
      y: 0,
      width: largeWidth,
      height: largeHeight
    };
    const barHeight = pedBounds.y + pedBounds.height / 2 + smallHeight / 2 + 2;
    const offLargeBounds: Bounds = {
      x: pedBounds.width / 2 - largeWidth / 2,
      y: pedBounds.y + pedBounds.height / 2 - smallHeight / 2 + barHeight - largeHeight,
      width: largeWidth,
      height: largeHeight,
    };

    const onSmallBounds: Bounds = {
      x: pedBounds.x + pedBounds.width / 2 - smallWidth / 2,
      y: pedBounds.y + pedBounds.height / 2 - smallHeight / 2,
      width: smallWidth,
      height: smallHeight,
    };
    onSmallBounds.y -= Math.floor((1.0 / 7.0) * pedBounds.height);

    const offSmallBounds: Bounds = {
      x: pedBounds.x + pedBounds.width / 2 - smallWidth / 2,
      y: pedBounds.y + pedBounds.height / 2 - smallHeight / 2,
      width: smallWidth,
      height: smallHeight,
    };
    offSmallBounds.y += Math.floor((1.0 / 7.0) * pedBounds.height);

    this.drawVerticalBar(g, offSmallBounds, offLargeBounds, false);
    this.drawVerticalBar(g, onSmallBounds, onLargeBounds, true);
  }

  private drawPedestal(g: G, bounds: Bounds) {
    g.addChild(new Ellipse({
      id: `${this.id}-ped`,
      cx: this.x + bounds.x + (bounds.width / 2),
      cy: this.y + bounds.y + (bounds.height / 2),
      rx: bounds.width / 2,
      ry: bounds.height / 2,
      fill: this.effect3d ? Color.WHITE : Color.GRAY,
    }));

    if (this.effect3d) {
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-ped-disabled`,
        x1: '0%', y1: '0%',
        x2: '100%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': 0 }),
        new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': (150 / 255) }),
      ));
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-ped-enabled`,
        x1: '0%', y1: '0%',
        x2: '100%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': (10 / 255) }),
        new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': (100 / 255) }),
      ));
      g.addChild(new Ellipse({
        id: `${this.id}-ped-effect`,
        cx: this.x + bounds.x + (bounds.width / 2),
        cy: this.y + bounds.y + (bounds.height / 2),
        rx: bounds.width / 2,
        ry: bounds.height / 2,
        fill: `url(#${this.id}-ped-disabled)`,
      }));
    }
  }

  private drawHorizontalBar(g: G, sm: Bounds, lg: Bounds, booleanValue: boolean) {
    const barG = new G({
      id: `${this.id}-bar-${booleanValue}`,
      visibility: booleanValue ? 'hidden' : 'visible', // OFF by default
      cursor: 'pointer',
    });
    g.addChild(barG);

    let stopOpacity1 = (booleanValue ? 0 : 10) / 255;
    let stopOpacity2 = (booleanValue ? 150 : 220) / 255;

    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-bar-g-lg-${booleanValue}`,
      x1: '0%', y1: '0%',
      x2: '0%', y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity1 }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity2 }),
    ));
    const offset = (100 * ((lg.height - sm.height) / sm.height)) / 2;
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-bar-g-sm-${booleanValue}`,
      x1: '0%', y1: `${-offset}%`,
      x2: '0%', y2: `${100 + offset}%`,
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity1 }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity2 }),
    ));

    barG.addChild(new Ellipse({
      cx: this.x + sm.x + (sm.width / 2),
      cy: this.y + sm.y + (sm.height / 2),
      rx: sm.width / 2,
      ry: sm.height / 2,
      fill: booleanValue ? this.onColor : this.offColor,
    }));
    if (this.effect3d) {
      barG.addChild(new Ellipse({
        cx: this.x + sm.x + (sm.width / 2),
        cy: this.y + sm.y + (sm.height / 2),
        rx: sm.width / 2,
        ry: sm.height / 2,
        fill: `url(#${this.id}-bar-g-sm-${booleanValue})`,
      }));
    }

    let points = `${this.x + lg.x + lg.width / 2},${this.y + lg.y}`;
    points += ` ${this.x + lg.x + lg.width / 2},${this.y + lg.y + lg.height}`;
    points += ` ${this.x + sm.x + sm.width / 2},${this.y + sm.y + sm.height}`;
    points += ` ${this.x + sm.x + sm.width / 2},${this.y + sm.y}`;
    barG.addChild(new Polygon({
      points,
      fill: booleanValue ? this.onColor : this.offColor,
    }));
    if (this.effect3d) {
      barG.addChild(new Polygon({
        points,
        fill: `url(#${this.id}-bar-g-lg-${booleanValue})`,
      }));
    }

    barG.addChild(new Ellipse({
      cx: this.x + lg.x + (lg.width / 2),
      cy: this.y + lg.y + (lg.height / 2),
      rx: lg.width / 2,
      ry: lg.height / 2,
      fill: booleanValue ? this.onColor : this.offColor,
    }));
    if (this.effect3d) {
      stopOpacity1 = (booleanValue ? 10 : 0) / 255;
      stopOpacity2 = (booleanValue ? 180 : 150) / 255;
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-bar-g2-${booleanValue}`,
        x1: '0%', y1: '0%',
        x2: '0%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity1 }),
        new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity2 }),
      ));
      barG.addChild(new Ellipse({
        cx: this.x + lg.x + (lg.width / 2),
        cy: this.y + lg.y + (lg.height / 2),
        rx: lg.width / 2,
        ry: lg.height / 2,
        fill: `url(#${this.id}-bar-g2-${booleanValue})`,
      }));
    }
  }

  private drawVerticalBar(g: G, sm: Bounds, lg: Bounds, booleanValue: boolean) {
    const barG = new G({
      id: `${this.id}-bar-${booleanValue}`,
      visibility: booleanValue ? 'hidden' : 'visible', // OFF by default
      cursor: 'pointer',
    });
    g.addChild(barG);

    let stopOpacity = (booleanValue ? 210 : 160) / 255;
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-bar-g-lg-${booleanValue}`,
      x1: '0%', y1: '0%',
      x2: '100%', y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': (10 / 255) }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity }),
    ));
    const offset = (100 * ((lg.width - sm.width) / sm.width)) / 2;
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-bar-g-sm-${booleanValue}`,
      x1: `-${offset}%`, y1: '0%',
      x2: `${100 + offset}%`, y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': (10 / 255) }),
      new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity }),
    ));

    barG.addChild(new Ellipse({
      cx: this.x + sm.x + (sm.width / 2),
      cy: this.y + sm.y + (sm.height / 2),
      rx: sm.width / 2,
      ry: sm.height / 2,
      fill: booleanValue ? this.onColor : this.offColor,
    }));
    if (this.effect3d) {
      barG.addChild(new Ellipse({
        cx: this.x + sm.x + (sm.width / 2),
        cy: this.y + sm.y + (sm.height / 2),
        rx: sm.width / 2,
        ry: sm.height / 2,
        fill: `url(#${this.id}-bar-g-sm-${booleanValue})`,
      }));
    }

    let points = `${this.x + lg.x},${this.y + lg.y + lg.height / 2}`;
    points += ` ${this.x + lg.x + lg.width},${this.y + lg.y + lg.height / 2}`;
    points += ` ${this.x + sm.x + sm.width},${this.y + sm.y + sm.height / 2}`;
    points += ` ${this.x + sm.x},${this.y + sm.y + sm.height / 2}`;
    barG.addChild(new Polygon({
      points,
      fill: booleanValue ? this.onColor : this.offColor,
    }));
    if (this.effect3d) {
      barG.addChild(new Polygon({
        points,
        fill: `url(#${this.id}-bar-g-lg-${booleanValue})`,
      }));
    }

    barG.addChild(new Ellipse({
      cx: this.x + lg.x + (lg.width / 2),
      cy: this.y + lg.y + (lg.height / 2),
      rx: lg.width / 2,
      ry: lg.height / 2,
      fill: booleanValue ? this.onColor : this.offColor,
    }));
    if (this.effect3d) {
      stopOpacity = (booleanValue ? 180 : 160) / 255;
      this.display.defs.addChild(new LinearGradient({
        id: `${this.id}-bar-g2-${booleanValue}`,
        x1: '0%', y1: '0%',
        x2: '0%', y2: '100%',
      }).addChild(
        new Stop({ offset: '0%', 'stop-color': Color.BLACK, 'stop-opacity': (10 / 255) }),
        new Stop({ offset: '100%', 'stop-color': Color.BLACK, 'stop-opacity': stopOpacity }),
      ));
      barG.addChild(new Ellipse({
        cx: this.x + lg.x + (lg.width / 2),
        cy: this.y + lg.y + (lg.height / 2),
        rx: lg.width / 2,
        ry: lg.height / 2,
        fill: `url(#${this.id}-bar-g2-${booleanValue})`,
      }));
    }
  }

  afterDomAttachment() {
    const pedestalEffectEl = this.svg.getElementById(`${this.id}-ped-effect`);
    const barOn = this.svg.getElementById(`${this.id}-bar-true`) as SVGGElement;
    const barOff = this.svg.getElementById(`${this.id}-bar-false`) as SVGGElement;

    barOn.addEventListener('click', () => {
      barOn.style.visibility = 'hidden';
      barOff.style.visibility = 'visible';
      if (this.effect3d) {
        pedestalEffectEl.setAttribute('fill', `url(#${this.id}-ped-disabled)`);
      }
    });
    barOff.addEventListener('click', () => {
      barOn.style.visibility = 'visible';
      barOff.style.visibility = 'hidden';
      if (this.effect3d) {
        pedestalEffectEl.setAttribute('fill', `url(#${this.id}-ped-enabled)`);
      }
    });
  }
}
