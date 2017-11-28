import { Component, OnInit, Input, Output, EventEmitter, ViewChild, SimpleChange } from '@angular/core';

import { SolrService } from '../../../solr.service';
import {Interval} from '../../../shared';
import { Facet } from '../../../shared/index';

declare var jQuery: any;

@Component({

  selector: 'app-timeline',
  templateUrl: 'timeline.component.html',
  styleUrls: ['timeline.component.css']
})
export class TimelineComponent implements OnInit {


  @ViewChild('container') container;
  @ViewChild('handleRight') handleRight;
  @ViewChild('handleLeft') handleLeft;
  @ViewChild('dst') dst;

  interval: Interval;

  @Output() timelineChanged = new EventEmitter();

  public leftPos: number = 0;
  public leftPosStr: string;
  public rightPos: number = 0;
  public rightPosStr: string;
  button_is_down_: boolean;


  private deltaLeft: number;
  private deltaRight: number;
  private movingLeft: boolean;
  private containerWidth: number = 0;
  private handleWidth: number = 0;

  private pxToObdobi: number;

  obdobi: any = null;
  obdobiCount: number;

  titleOd: string;
  titleDo: string;

  idxOd: number;
  idxDo: number;

  currentObdobi: any;

  subs: any;


  constructor(public solrService: SolrService) {
    this.leftPosStr = this.leftPos + 'px';
    this.rightPosStr = this.rightPos + 'px';
    

  }

  ngOnInit() {
      this.setObdobi();
  }
  
  setWidths(){
    if (this.container && this.container.nativeElement.offsetWidth) {
        this.containerWidth = this.container.nativeElement.offsetWidth;
        this.handleWidth = this.handleLeft.nativeElement.offsetWidth;
        this.pxToObdobi = (this.obdobiCount) / this.containerWidth;
        this.leftPos = 0;
        this.rightPos = 0;
        this.leftPosStr = this.leftPos + 'px';
        this.rightPosStr = this.rightPos + 'px';
        //this.posChanged();

      } 
  }

  setObdobi() {
    if (this.solrService.obdobi !== null && this.solrService.obdobi['response']['docs'].length > 0) {
      this.obdobi = this.solrService.obdobi['response']['docs'];
      this.obdobiCount = this.solrService.obdobi['stats']['stats_fields']['poradi']['count'];
      this.currentObdobi = '0,' + (this.obdobi.length - 1);
      this.setWidths();
      //this.interval = new Interval(this.poradiStats['min'], this.poradiStats['max']);
      this.titleOd = this.obdobi[0]['nazev'];
      this.titleDo = this.obdobi[this.obdobi.length - 1]['nazev'];
        
        this.subs = this.solrService.currentObdobiSubject.subscribe(val=> {
          this.currentObdobi = val;
          this.setPos();
        });
      //this.open();

    } else {
      setTimeout(() => {
        this.setObdobi();
      }, 100);
    }

  }

  setPos() {
    if (this.obdobi !== null && this.obdobi.length > 0) {
      let idx1 = parseInt(this.currentObdobi.split(',')[0]);
      let idx2 = parseInt(this.currentObdobi.split(',')[1]);
      this.leftPos = idx1 / this.pxToObdobi;
      this.rightPos = Math.floor(this.containerWidth - (idx2 / this.pxToObdobi));
      this.leftPosStr = this.leftPos + 'px';
      this.rightPosStr = this.rightPos + 'px';
      this.posChanged();
    } else {
      setTimeout(() => {
        this.setPos();
      }, 100);
    }
  }

  posChanged() {

    this.idxOd = Math.floor(this.leftPos * this.pxToObdobi);
    this.titleOd = this.obdobi[this.idxOd]['nazev'];
    this.idxDo = Math.min(Math.floor((this.containerWidth - this.rightPos) * this.pxToObdobi), this.obdobi.length - 1);
    this.titleDo = this.obdobi[this.idxDo]['nazev'];

  }

  addFilter() {
    let f: Facet = new Facet();
    f.field = 'obdobi_poradi';
    //f.value = this.idxOd + ',' + this.idxDo;
    f.value = this.idxOd + ',' + this.idxDo;
    this.solrService.setFilter(f.field, f.value);
  }

  ngAfterViewInit() {
    if (this.container.nativeElement.offsetWidth) {
      this.containerWidth = this.container.nativeElement.offsetWidth;
      this.handleWidth = this.handleLeft.nativeElement.offsetWidth;
    }
  }

  open() {
    if (this.obdobi === null) {
      this.setObdobi();
    }
    if (this.containerWidth === 0) {

      if (this.container && this.container.nativeElement.offsetWidth) {
        this.containerWidth = this.container.nativeElement.offsetWidth;
        this.handleWidth = this.handleLeft.nativeElement.offsetWidth;
        this.pxToObdobi = (this.obdobiCount) / this.containerWidth;
        this.leftPos = 0;
        this.rightPos = 0;
        this.leftPosStr = this.leftPos + 'px';
        this.rightPosStr = this.rightPos + 'px';
        this.posChanged();

      } else {
        setTimeout(() => {
          this.open();
        }, 10);
      }
    }

  }

  onMousedown(evt: any, isLeft: boolean) {
    evt.preventDefault();
    this.button_is_down_ = true;
    this.movingLeft = isLeft;
    if (this.movingLeft) {
      this.deltaLeft = evt.clientX - this.leftPos;
    } else {
      this.deltaRight = evt.clientX + this.rightPos;
    }
  }

  //
  // this function can only be called when button_is_down_ is true
  // as we have used a special div with *ngIf
  // <div *ngIf="button_is_down_"  (window:mousemove)="onMousemove($event)" ..
  //
  onMousemove(evt: any) {

    if (this.movingLeft) {
      if (evt.clientX - this.deltaLeft < 0) {
        this.leftPos = 0;
      } else {
        var newMargin = evt.clientX - this.deltaLeft;
        var offsetRight = this.containerWidth - this.rightPos - this.handleWidth;
        if (newMargin + this.handleWidth < offsetRight) {
          //jen pokud neni u praveho
          this.leftPos = newMargin;
        }
      }
      this.leftPosStr = this.leftPos + 'px';

    } else {
      if (this.deltaRight - evt.clientX > 0) {
        var nm = this.deltaRight - evt.clientX;
        var offsetLeft = this.leftPos + this.handleWidth;
        var offsetRight = this.containerWidth - nm - this.handleWidth;
        if (offsetRight > offsetLeft) {
          //jen pokud neni u leveho
          this.rightPos = nm;
        }
      } else {
        this.rightPos = 0;
      }
      this.rightPosStr = this.rightPos + 'px';
    }
    this.posChanged();
  }

  onMouseup(evt: any) {
    if (this.button_is_down_) {
      this.addFilter();
    }
    this.button_is_down_ = false;
  }

}
