import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { NalezComponent } from '../nalez/';
import { KomponentaDok, Akce, Nalez } from '../../../shared/models';

@Component({

  selector: 'app-komponenta-dok',
  templateUrl: 'komponenta-dok.component.html',
  styleUrls: ['komponenta-dok.component.css']
})
export class KomponentaDokComponent implements OnInit {

  @Input() docId: string;
  @Input() komponenta: KomponentaDok;

  nalez: Nalez[] = [];
  aktivity: string[] = [];

  constructor(public solrService: SolrService) {}

  ngOnInit() {
        this.solrService.getNalezKomponentaDok(this.komponenta.ident_cely).subscribe(res => {
          for(let doc in res){
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
    return this.komponenta[field];
  }


  id(){
    return this.docId + '_' + this.komponenta.ident_cely;
  }


  hasValue(field: string): boolean{
    return this.solrService.hasValue(field, this.komponenta);
  }
  
  idShort(){
    
    //console.log(this.komponenta, this.docId);
    return this.komponenta.ident_cely.replace(this.docId.replace('dokument_', '')+'-', '');
  }
	
}
