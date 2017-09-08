import { Routes } from '@angular/router';

import {
    sessionsRoute
} from './';

const ACCOUNT_ROUTES = [
    sessionsRoute
];

export const accountState: Routes = [{
    path: '',
    children: ACCOUNT_ROUTES
}];
