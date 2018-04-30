import { Injector } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import 'rxjs/add/operator/do';
import { LoginService } from '../../shared/login/login.service';
import { StateStorageService } from '../../shared/auth/state-storage.service';

export class AuthExpiredInterceptor implements HttpInterceptor {

    constructor(
        private stateStorageService: StateStorageService,
        private injector: Injector
    ) {}

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return next.handle(request).do((event: HttpEvent<any>) => {}, (err: any) => {
            if (err instanceof HttpErrorResponse) {
                if (err.status === 401 && err.url && !err.url.includes('/api/account')) {
                    const destination = this.stateStorageService.getDestinationState();
                    if (destination !== null) {
                        const to = destination.destination;
                        const toParams = destination.params;
                        if (to.name === 'accessdenied') {
                            this.stateStorageService.storePreviousState(to.name, toParams);
                        }
                    } else {
                        this.stateStorageService.storeUrl('/');
                    }

                    const loginService: LoginService = this.injector.get(LoginService);
                    loginService.login();

                }
            }
        });
    }
}
