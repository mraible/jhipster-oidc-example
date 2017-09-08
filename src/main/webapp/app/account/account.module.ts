import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { OjoSharedModule } from '../shared';

import {
    SessionsService,
    SessionsComponent,
    accountState
} from './';

@NgModule({
    imports: [
        OjoSharedModule,
        RouterModule.forRoot(accountState, { useHash: true })
    ],
    declarations: [
        SessionsComponent
    ],
    providers: [
        SessionsService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class OjoAccountModule {}
