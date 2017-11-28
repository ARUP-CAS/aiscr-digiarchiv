import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { Nalez } from '../../../shared/models';


@Component({

  selector: 'app-nalez',
  templateUrl: 'nalez.component.html',
  styleUrls: ['nalez.component.css']
})
export class NalezComponent implements OnInit {

  @Input() docId: string;
  @Input() nalez: Nalez;

  constructor(public solrService: SolrService) {}

  ngOnInit() {
  }

  id(){
    return 'nalez-' + this.docId + '-' + this.nalez['uniqueid'];
  }

	hasValue(field: string): boolean{
    return this.solrService.hasValue(field, this.nalez);
  }
	
}
