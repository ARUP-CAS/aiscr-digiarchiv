import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { Nalez } from '../../../shared/models';


@Component({

  selector: 'app-tvar',
  templateUrl: 'tvar.component.html',
  styleUrls: ['tvar.component.css']
})
export class TvarComponent implements OnInit {

  @Input() docId: string;
  @Input() tvar: string;
  @Input() poznamka: string = null;

  constructor(public solrService: SolrService) {}

  ngOnInit() {
  }

  id(){
    return 'tvar-' + this.docId + "_1";
  }

  hasPoznamka(): boolean{
    return this.poznamka !== null && this.poznamka !== '';
  }
	
}
