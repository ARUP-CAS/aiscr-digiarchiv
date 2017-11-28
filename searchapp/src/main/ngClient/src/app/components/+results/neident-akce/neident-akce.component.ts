import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { NeidentAkce } from '../../../shared/models';

@Component({

  selector: 'app-neident-akce',
  templateUrl: 'neident-akce.component.html',
  styleUrls: ['neident-akce.component.css']
})
export class NeidentAkceComponent implements OnInit {

  @Input() docId: string;
  @Input() data: NeidentAkce;

  constructor(public solrService: SolrService) {}

  ngOnInit() {
  }

  id(){
    return this.docId + '_' + this.data.ident_cely;
  }


  hasValue(field: string): boolean{
    return this.solrService.hasValue(field, this.data);
  }
  
  
  idShort(){
    return this.data.ident_cely.replace(this.docId.replace('dokument_', '')+'-', '');
  }

}
