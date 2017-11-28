import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { KomponentaComponent } from '../komponenta/';
import { Komponenta, DokJednotka } from '../../../shared/models';

@Component({

  selector: 'app-dok-jednotka',
  templateUrl: 'dok-jednotka.component.html',
  styleUrls: ['dok-jednotka.component.css']
})
export class DokJednotkaComponent implements OnInit {

  @Input() docId: string;
  @Input() dok: DokJednotka;

  komponenty: Komponenta[] = [];

  constructor(public solrService: SolrService) { }

  ngOnInit() {
    if (this.dok) {
      this.solrService.getKomponenty(this.dok['ident_cely']).subscribe(res => {
        for (let doc in res) {
          this.komponenty.push(res[doc]);
        }
      });
    }
  }

  id() {
    return this.docId + '_' + this.dok['ident_cely'];
  }
	
	hasValue(field: string): boolean {
    return this.solrService.hasValue(field, this.dok);
  }
  idShort(){
    
    return this.dok.ident_cely[0].replace(this.dok.parent[0]+'-', '');
  }

}
