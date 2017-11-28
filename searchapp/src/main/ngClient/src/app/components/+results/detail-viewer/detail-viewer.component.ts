import { Component, OnInit, ViewChild, Output, EventEmitter } from '@angular/core';


import { ModalComponent } from 'ng2-bs3-modal/ng2-bs3-modal';

import { SolrService } from '../../../solr.service';
import { ResultItemComponent } from '../result-item/';

@Component({

  selector: 'app-detail-viewer',
  templateUrl: 'detail-viewer.component.html',
  styleUrls: ['detail-viewer.component.css']
})
export class DetailViewerComponent implements OnInit {

  @ViewChild('modal') modal: ModalComponent;

  showing: boolean = false;
  results: any[] = [];

  @Output() onViewFile = new EventEmitter();

  constructor(public solrService: SolrService) { }

  ngOnInit() {

  }

  open(result) {
    console.log(result.ident_cely);
    this.results = [];
    this.results.push(result);
    this.modal.open();
    this.showing = true;
  }


  close() {
    this.modal.close();
    setTimeout(() => {
      this.results = [];
      this.showing = false;
    }, 200);

  }

  viewFile() {
    console.log("emitting...");
    this.onViewFile.emit(this.results[0]);
  }


}
