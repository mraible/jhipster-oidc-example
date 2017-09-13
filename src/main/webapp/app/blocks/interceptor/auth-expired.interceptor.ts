import { JhiHttpInterceptor } from 'ng-jhipster';
import { RequestOptionsArgs, Response } from '@angular/http';
import { Observable } from 'rxjs/Observable';
import { Injector } from '@angular/core';
import { AuthServerProvider } from '../../shared/auth/auth-session.service';
import { StateStorageService } from '../../shared/auth/state-storage.service';
import { LoginService } from '../../shared/login/login.service';

export class AuthExpiredInterceptor extends JhiHttpInterceptor {

    constructor(private injector: Injector,
        private stateStorageService: StateStorageService) {
        super();
    }

    requestIntercept(options?: RequestOptionsArgs): RequestOptionsArgs {
        return options;
    }

    responseIntercept(observable: Observable<Response>): Observable<Response> {
        return <Observable<Response>> observable.catch((error) => {
            if (error.status === 401 && error.text() !== '' && error.json().path && error.json().path.indexOf('/api/account') === -1) {
                const authServerProvider = this.injector.get(AuthServerProvider);
                const destination = this.stateStorageService.getDestinationState();
                const to = destination.destination;
                const toParams = destination.params;
                authServerProvider.logout();

                if (to.name === 'accessdenied') {
                    this.stateStorageService.storePreviousState(to.name, toParams);
                }

                const loginService = this.injector.get(LoginService);
                loginService.login();

            }
            return Observable.throw(error);
        });
    }
}
