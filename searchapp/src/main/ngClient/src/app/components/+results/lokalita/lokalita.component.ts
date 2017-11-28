import { Component, OnInit, Input } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { Lokalita } from '../../../shared/models';
import { DokJednotkaComponent } from '../dok-jednotka';

@Component({

  selector: 'app-lokalita',
  templateUrl: 'lokalita.component.html',
  styleUrls: ['lokalita.component.css']
})
export class LokalitaComponent implements OnInit {

  @Input() data: Lokalita;
  @Input() docId: string;

  dokJednotky: any[] = [];
  extZdroj: any[] = [];
  opened: boolean = false;

  constructor(public solrService: SolrService) { }

  ngOnInit() {
    this.getDokJednotky();
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
	
	okres() {
    if (this.data.hasOwnProperty('okres')) {
      let okresy = [];
      let katastry = [];
      let ret = "";
      for (let idx = 0; idx < this.data['okres'].length; idx++) {
        let okres = this.data['okres'][idx];
        let katastr = this.data['katastr'][idx];

        if (katastry.indexOf(katastr) < 0) {
          okresy.push(okres);
          katastry.push(katastr);
          if (idx > 0) {
            ret += ', ';
          }
          ret += katastr + ' (' + okres + ')';
        }
      }
      return ret;
    } else {
      return "";
    }
  }
  
  
  idShort(){
    return this.data.ident_cely[0].replace(this.docId.replace('dokument_', '')+'-', '');
  }

}
