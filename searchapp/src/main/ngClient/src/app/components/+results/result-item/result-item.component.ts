import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { NeidentAkce, Akce, Nalez, Lokalita, KomponentaDok } from '../../../shared/models';

@Component({

  selector: 'app-result-item',
  templateUrl: 'result-item.component.html',
  styleUrls: ['result-item.component.css']
})
export class ResultItemComponent implements OnInit {

  @Input() result;
  @Input() highlighting;
  @Input() inModal: boolean = false;

  @Output() onViewFile = new EventEmitter();
  @Output() onViewDetail = new EventEmitter();

  autor: string;
  files: any = [];
  lokality: Lokalita[] = [];
  akce: Akce[] = [];
  komponentaDok: KomponentaDok[] = [];
  neidentAkce: NeidentAkce[] = [];
  numNalezu: number = 0;

  isFav: boolean = false;
  showNalezy: boolean = false;

  detailExpanded: boolean = false;
  toggleText: string = 'Zobrazit detail';

  public versions: any[] = [];
  constructor(public solrService: SolrService) { }

  ngOnInit() {
    
    this.formatAutor();

    if (this.result.hasOwnProperty('soubor')) {
      this.files = JSON.parse(this.result.soubor[0]);
    }

    if (this.result.hasOwnProperty('akce_ident_cely')) {
      for (let idx in this.result.akce_ident_cely) {
        if(this.result.akce_ident_cely[idx] !== ''){
          this.solrService.getAkce(this.result.akce_ident_cely[idx]).subscribe(res => {
            this.akce.push(res);
          });
        }
      }
    }

    if (this.result.hasOwnProperty('lokalita_ident_cely')) {
      for (let idx in this.result.lokalita_ident_cely) {
        if(this.result.lokalita_ident_cely[idx] !== ''){
          this.solrService.getLokalita(this.result.lokalita_ident_cely[idx]).subscribe(res => {
            this.lokality.push(res);
          });
        }
      }
    }

    if (this.result.hasOwnProperty('neident_akce_ident_cely')) {
      for (let idx in this.result['neident_akce_ident_cely']) {
        let k = new NeidentAkce();
        k.ident_cely = this.result['neident_akce_ident_cely'][idx];
        k.setFieldFromDokument(this.result, idx);
        this.neidentAkce.push(k);
        this.neidentAkce.sort((a,b) => {
          return a.ident_cely > b.ident_cely ? 1 : -1;
        });
      }
    }
    if (this.result.hasOwnProperty('komponenta_dokumentu_ident_cely')) {
      for (let idx in this.result['komponenta_dokumentu_ident_cely']) {
        let k = new KomponentaDok();

        k.setFieldFromDokument(this.result, idx);
        this.komponentaDok.push(k);
        this.komponentaDok.sort((a,b) => {
          return parseInt(a.poradi) - parseInt(b.poradi);
        });
      }
    }

    if (this.result.hasOwnProperty('nalez_druh_nalezu')) {
      this.numNalezu = this.result.nalez_druh_nalezu.length;
    }
    
    this.getIsFav();
  }
  
  formatAutor(){
    if(this.result){
      let autors : string[] = this.result['autor']
      this.autor = autors.join("; ");
    }
  }
  
  hasPopisletu(){
    return this.result.hasOwnProperty('let_ident_cely');
  }
  
  hasTvar(){
    return this.result.hasOwnProperty('tvar_dokument') && this.result['tvar_dokument'].length >0 ;
  }

  okres() {
    if (this.result.hasOwnProperty('f_okres')) {
      let okresy = [];
      let katastry = [];
      let ret = "";
      for (let idx = 0; idx < this.result['f_okres'].length; idx++) {
        let okres = this.result['f_okres'][idx];
        let katastr = this.result['f_katastr'][idx];

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

  organizace() {
    if (this.result.hasOwnProperty('organizace')) {
      let os = [];
      let ret = "";
      for (let idx = 0; idx < this.result['organizace'].length; idx++) {
        let org = this.result['organizace'][idx];
        if (org) {
          org = org.trim();
        }

        let o = org ? this.solrService.getTranslation(org, 'organizace') : '';
        if ((o !== '') && (os.indexOf(o) < 0)) {
          os.push(o);

          if (idx > 0) {
            ret += ', ';
          }
          ret += o;
        }

      }
      return ret;
    } else {
      return "";
    }
  }

  viewFile() {
    if(this.solrService.hasRights(this.result['pristupnost'])){
      this.onViewFile.emit(this.result);
    } else {
//    this.onViewFile.emit(this.result);
      let msg = this.solrService.translateKey('insuficient rights');
      alert(msg);
    }
  }

  openDetail() {
    this.onViewDetail.emit(this.result);
  }

  gotoDoc(id: string) {
    this.solrService.gotoDoc(id, false);
  }

  hasValue(field: string): boolean {
    if (this.result.hasOwnProperty(field)) {
      if (typeof this.result[field] === "string") {
        return this.result[field].trim() !== '';
      } else {
        return this.result[field][0].trim() !== '';
      }

    } else {
      return false;
    }
  }

  popisObsahu(): string {
    let s: string = this.result.popis;
    return s.replace(/\[new_line\]/gi, '<br/>');
  }
 
  id() {
    if (this.inModal) {
      return 'modal_' + this.result['uniqueid'];
    } else {
      return this.result['uniqueid'];
    }
  }

  print(id: string) {
    if (this.solrService.route === 'document') {
      window.print();
    } else {
      this.solrService.gotoDoc(id, true);
    }
  }

  getIsFav() {
    if (!this.solrService.config['logged']) {
      this.isFav = false;
    } else {
      this.solrService.getIsFav(this.result['uniqueid']).subscribe(res => {
        this.isFav = res;
      });
    }
  }

  toggleFav() {
    if (this.isFav) {
      this.solrService.removeFav(this.result['uniqueid']).subscribe(res => {
        this.isFav = false;
      });
    } else {
      this.solrService.addFav(this.result['uniqueid']).subscribe(res => {
        this.isFav = true;
      });
    }
  }

  toggleDetail() {
    this.detailExpanded = !this.detailExpanded;
    this.toggleText = this.detailExpanded ? 'Skrýt detail' : 'Zobrazit detail';
  }

}
