import { Component, OnInit, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';

import { SolrService } from '../../solr.service';
import { HeaderComponent } from '../header/index';
import { TimelineComponent } from './timeline/';
import { Filter } from '../../shared/index';
import { FacetsComponent } from './facets/';
import { ResultItemComponent } from './result-item/';
import { PaginationComponent } from './pagination/';
import { FileViewerComponent } from './file-viewer/';
import { DetailViewerComponent } from './detail-viewer/';
import {Interval} from '../../shared';


@Component({

  selector: 'app-results',
  templateUrl: 'results.component.html',
  styleUrls: ['results.component.css']
})
export class ResultsComponent implements OnInit {

  @ViewChild('fileViewer') fileViewer: FileViewerComponent;
  @ViewChild('detailViewer') detailViewer: DetailViewerComponent;
  @ViewChild('timeline') timeline: TimelineComponent;

  interval: Interval = new Interval(1900, 2016);
  
  docs = [];

  constructor(private titleService: Title, public solrService: SolrService) { }

  ngOnInit() {
    this.titleService.setTitle('Digitální archiv AMČR | Results');
    this.solrService.docsSubject.subscribe(val => {
      
      this.docs = [];
      this.docs = val;
    });

  }

  onViewFile(data) {
    this.fileViewer.openModal(data);
  }

  onViewDetail(doc) {
    this.detailViewer.open(doc);
  }

  onOpenTimeline() {
    this.timeline.open();
  }

}
