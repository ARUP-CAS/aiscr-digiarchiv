import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { Komponenta } from '../../../shared/models';

@Component({

  selector: 'app-externi-zdroj',
  templateUrl: 'externi-zdroj.component.html',
  styleUrls: ['externi-zdroj.component.css']
})
export class ExterniZdrojComponent implements OnInit {

  @Input() docId: string;
  @Input() data: any;

  constructor(public solrService: SolrService) {}

  ngOnInit() {
  }

  hasValue(field: string): boolean{
    return this.solrService.hasValue(field, this.data);
  }

  id(){
    return this.docId + '_' + this.data.ident_cely;
  }

}
