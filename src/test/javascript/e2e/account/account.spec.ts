import { browser, element, by } from 'protractor';
import { NavBarPage, SignInPage } from './../page-objects/jhi-page-objects';

describe('account', () => {

    let navBarPage: NavBarPage;
    let signInPage: SignInPage;

    beforeAll(() => {
        browser.get('/');
        browser.waitForAngular();
        navBarPage = new NavBarPage(true);
        browser.waitForAngular();
    });

    it('should fail to login with bad password', () => {
        const expect1 = /home.title/;
        element.all(by.css('h1')).first().getAttribute('jhiTranslate').then((value) => {
            expect(value).toMatch(expect1);
        });
        signInPage = navBarPage.getSignInPage();
        browser.waitForAngularEnabled(false);
        signInPage.loginWithOAuth('admin', 'foo');

        // keycloak
        const expect2 = "Invalid username or password.";
        const error = element.all(by.css('.alert-error')).first().getText();
        expect(error).toMatch(expect2);
    });

    it('should login successfully with admin account', () => {
        signInPage.clearUserName();
        signInPage.setUserName('admin');
        signInPage.clearPassword();
        signInPage.setPassword('admin');
        signInPage.login();

        browser.waitForAngular();

        const expect2 = /home.logged.message/;
        element.all(by.css('.alert-success span')).getAttribute('jhiTranslate').then((value) => {
            expect(value).toMatch(expect2);
        });
    });

    afterAll(() => {
        navBarPage.autoSignOut();
    });
});
