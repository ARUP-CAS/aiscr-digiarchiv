export class NeidentAkce {
  ident_cely: string;
  okres: string;
  katastr:string;
  lokalizace:string;
  vedouci:string;
  rok_zahajeni:string;
  rok_ukonceni:string;
  pian:string;
  popis:string;
  poznamka:string;
  
  setFieldFromDokument(dk: any, idx: string){
    this.okres = dk['neident_akce_okres'][idx];
    this.katastr = dk['neident_akce_katastr'][idx];
    this.lokalizace = dk['neident_akce_lokalizace'][idx];
    this.vedouci = dk['neident_akce_vedouci'][idx];
    this.rok_zahajeni = dk['neident_akce_rok_zahajeni'][idx];
    this.rok_ukonceni = dk['neident_akce_rok_ukonceni'][idx];
    if(dk.hasOwnProperty('neident_akce_pian')){
      this.pian = dk['neident_akce_pian'][idx];
    }
    this.popis = dk['neident_akce_popis'][idx];
    this.poznamka = dk['neident_akce_poznamka'][idx];
  }
}