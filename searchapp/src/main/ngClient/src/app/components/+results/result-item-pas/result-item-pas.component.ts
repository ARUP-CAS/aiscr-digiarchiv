import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { SolrService } from '../../../solr.service';
import { NeidentAkce, Akce, Nalez, Lokalita, KomponentaDok } from '../../../shared/models';

@Component({

  selector: 'app-result-item-pas',
  templateUrl: 'result-item-pas.component.html',
  styleUrls: ['result-item-pas.component.css']
})
export class ResultItemPasComponent implements OnInit {

  @Input() result;
  @Input() highlighting;
  @Input() inModal: boolean = false;

  @Output() onViewFile = new EventEmitter();
  @Output() onViewDetail = new EventEmitter();

  detailFields = [
  // "projekt_id",
  "pocet",
  "nalezove_okolnosti",
  "hloubka",
  "poznamka",
  // "geom_x",
  // "geom_y",
  // "obdobi",
  // "presna_datace",
  // "typ",
  // "druh",
  // "specifikace",
  // "stav",
  // "stav_popis",
  // "predano"
]

  files: any = [];
  lokality: Lokalita[] = [];
  
  numNalezu: number = 0;

  isFav: boolean = false;
  showNalezy: boolean = false;

  detailExpanded: boolean = false;
  toggleText: string = 'Zobrazit detail';

  public versions: any[] = [];
  hasRights = false;

  constructor(public solrService: SolrService) { }

  ngOnInit() {
    
    if (this.result.hasOwnProperty('soubor')) {
      this.files = JSON.parse(this.result.soubor[0]);
    }

    if (this.result.hasOwnProperty('nalez_druh_nalezu')) {
      this.numNalezu = this.result.nalez_druh_nalezu.length;
    }

    this.hasRights = this.solrService.hasRights(this.result['pristupnost']);
    
    if(this.hasRights) {
      this.detailFields.splice(2, 0, "lokalizace");
    }
    
    this.getIsFav();
  }
  
  
  
  hasPopisletu(){
    return this.result.hasOwnProperty('let_ident_cely');
  }
  
  hasTvar(){
    return this.result.hasOwnProperty('tvar_dokument') && this.result['tvar_dokument'].length >0 ;
  }

  katastr() {
    if (this.result.hasOwnProperty('f_katastr')) {
      let katastry = [];
      let ret = "";
      for (let idx = 0; idx < this.result['f_okres'].length; idx++) {
        
        let katastr = this.result['f_katastr'][idx];

        if (katastry.indexOf(katastr) < 0) {
          katastry.push(katastr);
          if (idx > 0) {
            ret += ', ';
          }
          ret += katastr;
        }
      }
      return ret;
    } else {
      return "";
    }
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
