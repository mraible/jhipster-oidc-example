import { Injectable } from '@angular/core';
import { Principal } from '../auth/principal.service';
import { AuthServerProvider } from '../auth/auth-session.service';

@Injectable()
export class LoginService {

    constructor(private principal: Principal,
                private authServerProvider: AuthServerProvider) {
    }

    login() {
        let port = (location.port ? ':' + location.port : '');
        if (port === ':9000') {
            port = ':8080';
        }
        const loginUrl = '//' + location.hostname + port + '/login';
        console.log('Sending to IdP login at: ' + loginUrl);
        location.href = loginUrl;
    }

    logout() {
        this.authServerProvider.logout().subscribe();
        this.principal.authenticate(null);
    }
}
