import { Component, OnInit, Input } from '@angular/core';
import { Akce } from '../../../shared/models';
import { SolrService } from '../../../solr.service';
import { DokJednotkaComponent } from '../dok-jednotka';
import { ExterniZdrojComponent } from '../externi-zdroj';

@Component({

  selector: 'app-akce',
  templateUrl: 'akce.component.html',
  styleUrls: ['akce.component.css']
})
export class AkceComponent implements OnInit {

  @Input() data: Akce;
  @Input() docId: string;

  dokJednotky: any[] = [];
  extZdroj: any[] = [];
  opened: boolean = false;
  
  

  constructor(public solrService: SolrService) { }

  ngOnInit() {
    this.formatAutor();
    this.getDokJednotky();
  }
  
  
  formatAutor(){
    if(this.data && this.data.hasOwnProperty('vedouci_akce_ostatni')){
      this.data['vedouci_akce_ostatni_formated'] = this.data['vedouci_akce_ostatni'].join("; ");
//      console.log(this.data['vedouci_akce_ostatni']);
//      this.data['vedouci_akce_ostatni'].forEach(va => {
//        let autors : string[] = va;
//        this.data['vedouci_akce_ostatni_formated'] = autors.join("; ");
//      });
    }
  }

  keys(): Array<string> {
    let keys = [];
    let ks = Object.keys(this.data);
    for (let k in ks) {
      if (this.data[ks[k]] != '') {
        keys.push(ks[k]);
      }
    }
    return keys;
  }

  hasValue(field: string): boolean {
    return this.solrService.hasValue(field, this.data);
  }

  id() {
    return this.docId + '_' + this.data.ident_cely;
  }

  getDokJednotky() {
    if (!this.opened) {
      this.solrService.getDokJednotky(this.data['ident_cely']).subscribe(res => {
        this.dokJednotky = res;
      });
      this.getExterniOdkaz();
      this.opened = true;
    }
  }


  getExterniOdkaz() {
    this.solrService.getExterniOdkaz(this.data['ident_cely']).subscribe(res => {
      for (let i in res) {
        this.getExterniZdroj(res[i]['externi_zdroj']);
      }
    });
  }


  getExterniZdroj(id: string) {
    this.solrService.getExterniZdroj(id).subscribe(res => {
      for (let i in res) {
        this.extZdroj.push(res[i]);
      }
    });
  }
  
  poznamka(){
    let s: string = this.data['poznamka'][0];
    return s.replace(/\[new_line\]/gi, '<br/>');
    //return this.data['poznamka'];
  }
  
  
  idShort(){
    return this.data.ident_cely[0].replace(this.docId.replace('dokument_', '')+'-', '');
  }
}
