import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { NalezComponent } from '../nalez/';
import { Komponenta, Akce, Nalez } from '../../../shared/models';

@Component({

  selector: 'app-komponenta',
  templateUrl: 'komponenta.component.html',
  styleUrls: ['komponenta.component.css']
})
export class KomponentaComponent implements OnInit {

  @Input() docId: string;
  @Input() data: Komponenta;

  nalez: Nalez[] = [];
  aktivity: string[] = [];

  constructor(public solrService: SolrService) { }

  ngOnInit() {
    this.solrService.getNalez(this.data.ident_cely).subscribe(res => {
      for (let doc in res) {
        this.nalez.push(res[doc]);
      }
    });
        this.fillAktivity();
  }
  
  fillAktivity(){
    this.hasAktivita('aktivita_sidlistni') ? this.aktivity.push('sidlistni') : '';
    this.hasAktivita('aktivita_tezebni') ? this.aktivity.push('tezebni') : '';
    this.hasAktivita('aktivita_vyrobni') ? this.aktivity.push('vyrobni') : '';
    this.hasAktivita('aktivita_komunikace') ? this.aktivity.push('komunikace') : '';
    this.hasAktivita('aktivita_boj') ? this.aktivity.push('boj') : '';
    this.hasAktivita('aktivita_kultovni') ? this.aktivity.push('kultovni') : '';
    this.hasAktivita('aktivita_pohrebni') ? this.aktivity.push('pohrebni') : '';
    this.hasAktivita('aktivita_deponovani') ? this.aktivity.push('deponovani') : '';
    this.hasAktivita('aktivita_intruze') ? this.aktivity.push('intruze') : '';
    this.hasAktivita('aktivita_jina') ? this.aktivity.push('jina') : '';
  }
  

  hasValue(field: string): boolean {
    return this.solrService.hasValue(field, this.data);
  }
  
  hasAnyAktivita(){
    return this.hasAktivita('aktivita_sidlistni') ||
			this.hasAktivita('aktivita_tezebni') ||
			this.hasAktivita('aktivita_vyrobni') ||
			this.hasAktivita('aktivita_komunikace') ||
			this.hasAktivita('aktivita_boj') ||
			this.hasAktivita('aktivita_kultovni') ||
			this.hasAktivita('aktivita_pohrebni') ||
			this.hasAktivita('aktivita_deponovani') ||
			this.hasAktivita('aktivita_intruze') ||
			this.hasAktivita('aktivita_jina');
  }
  
  hasAktivita(field: string){
    return this.data.hasOwnProperty(field) && this.data[field][0] === '1';
  }


  id() {
    return this.docId + '_' + this.data.ident_cely;
  }
  
  idShort(){
    
    if(this.data.ident_cely){
      let s = this.data.ident_cely[0];
      let p = s.lastIndexOf('-');
      return s.substring(p+1, s.length);
    } else {
      return '';
    }
  }

}
