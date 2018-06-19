import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule, JsonpModule, Http } from '@angular/http';
import { RouterModule }   from '@angular/router';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import {TranslateHttpLoader} from '@ngx-translate/http-loader';
import { SlimLoadingBarModule } from 'ng2-slim-loading-bar';
import { Ng2Bs3ModalModule } from 'ng2-bs3-modal/ng2-bs3-modal';

import { PageNotFoundComponent } from './components/page-not-found/page-not-found.component';

import { TranslateHeslar } from './translate-heslar.pipe'
import { AppComponent } from './app.component';
import { HeaderComponent } from './components/header/header.component';
import { FooterComponent } from './components/footer/footer.component';
import { HomeComponent } from './components/home/home.component';
import { SearchFormComponent } from './components/search-form/';
import { DocumentComponent } from './components/document/';
import {ResultsComponent,
    AkceComponent,
    BreadcrumbsComponent,
    DetailViewerComponent,
    DokJednotkaComponent,
    ExportComponent,
    ExterniZdrojComponent,
    FacetsComponent,
    FileViewerComponent,
    KomponentaComponent,
    KomponentaDokComponent,
    LokalitaComponent,
    MapaComponent,
    NalezComponent,
    NeidentAkceComponent,
    PaginationComponent,
    ResultItemComponent,
    TimelineComponent,
    TvarComponent} from './components/+results/';
import { FreeTextComponent } from './components/free-text/free-text.component';
    
    // AoT requires an exported function for factories
export function HttpLoaderFactory(http: Http) {
  return new TranslateHttpLoader(http, '/assets/i18n/', '');
}


@NgModule({
  declarations: [
    AppComponent,
    HeaderComponent,
    FooterComponent,
    HomeComponent,
    SearchFormComponent,

    PageNotFoundComponent,

    ResultsComponent,
    AkceComponent,
    BreadcrumbsComponent,
    DetailViewerComponent,
    DokJednotkaComponent,
    ExportComponent,
    ExterniZdrojComponent,
    FacetsComponent,
    FileViewerComponent,
    KomponentaComponent,
    KomponentaDokComponent,
    LokalitaComponent,
    MapaComponent,
    NalezComponent,
    NeidentAkceComponent,
    PaginationComponent,
    ResultItemComponent,
    TimelineComponent,
    TvarComponent,
    
    DocumentComponent,

    TranslateHeslar,

    FreeTextComponent

  ],
  imports: [
    BrowserModule,
    FormsModule,
    RouterModule.forRoot([
      { path: 'home', component: HomeComponent },
      { path: 'results', component: ResultsComponent },
      { path: 'id/:id', component: DocumentComponent },
      { path: 'export', component: ExportComponent },
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      //{ path: '**', component: PageNotFoundComponent }
    ]),
    HttpModule,
    JsonpModule,
    TranslateModule.forRoot({
            loader: {
                provide: TranslateLoader,
                useFactory: HttpLoaderFactory,
                deps: [Http]
            }
        }),
    Ng2Bs3ModalModule,
    SlimLoadingBarModule.forRoot()
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
