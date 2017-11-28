import { Component, ViewChild } from '@angular/core';
import {DecimalPipe} from '@angular/common';


import 'rxjs/add/operator/map';
import 'rxjs/add/operator/pluck';

import { SolrService } from './solr.service';
import { ResultsComponent } from './components/+results';

@Component({

  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.css'],
  providers: [SolrService, DecimalPipe]
})
export class AppComponent {

  @ViewChild('results') results: ResultsComponent;

  constructor(public solrService: SolrService) { }
  onOpenTimeline() {
    this.results.onOpenTimeline();
  }
}
