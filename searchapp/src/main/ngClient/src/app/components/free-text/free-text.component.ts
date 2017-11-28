import { Component, OnInit, Input } from '@angular/core';
import {TranslateService, LangChangeEvent} from '@ngx-translate/core';
import { SolrService } from '../../solr.service';

@Component({
  selector: 'app-free-text',
  templateUrl: './free-text.component.html',
  styleUrls: ['./free-text.component.css']
})
export class FreeTextComponent implements OnInit {
  
  @Input() id : string;
  text: string;

  constructor(private solrService: SolrService, 
  private translate: TranslateService) { }

  ngOnInit() {
    
    if(this.solrService.config){
      this.setText();
    }else{
      this.solrService.configObservable.subscribe(val=>{
        this.setText();
      });
    }
    
    this.translate.onLangChange.subscribe((event: LangChangeEvent) => {
      this.setText();
    });
    
  }
  
  setText(){
    this.solrService.getText(this.id).subscribe(t => this.text = t);
  }

}
