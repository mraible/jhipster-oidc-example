package com.okta.developer.web.rest;

import com.codahale.metrics.annotation.Timed;

import com.okta.developer.domain.Authority;
import com.okta.developer.domain.PersistentToken;
import com.okta.developer.domain.User;
import com.okta.developer.repository.PersistentTokenRepository;
import com.okta.developer.repository.UserRepository;
import com.okta.developer.security.SecurityUtils;
import com.okta.developer.service.MailService;
import com.okta.developer.service.UserService;
import com.okta.developer.service.dto.UserDTO;
import com.okta.developer.web.rest.vm.KeyAndPasswordVM;
import com.okta.developer.web.rest.vm.ManagedUserVM;
import com.okta.developer.web.rest.util.HeaderUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.*;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final PersistentTokenRepository persistentTokenRepository;

    public AccountResource(UserRepository userRepository, UserService userService,
                           PersistentTokenRepository persistentTokenRepository) {

        this.userRepository = userRepository;
        this.userService = userService;
        this.persistentTokenRepository = persistentTokenRepository;
    }

    /**
     * GET  /authenticate : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request
     * @return the login if the user is authenticated
     */
    @GetMapping("/authenticate")
    @Timed
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * GET  /account : get the current user.
     *
     * @return the ResponseEntity with status 200 (OK) and the current user in body, or status 500 (Internal Server Error) if the user couldn't be returned
     */
    @GetMapping("/account")
    @Timed
    @SuppressWarnings("unchecked")
    public ResponseEntity<UserDTO> getAccount(Principal principal) {
        OAuth2Authentication authentication = (OAuth2Authentication) principal;
        LinkedHashMap<String, Object> details = (LinkedHashMap) authentication.getUserAuthentication().getDetails();
        User user = new User();
        user.setLogin(details.get("preferred_username").toString());

        if (details.get("given_name") != null) {
            user.setFirstName(details.get("given_name").toString());
        }
        if (details.get("family_name") != null) {
            user.setFirstName(details.get("family_name").toString());
        }
        if (details.get("email_verified") != null) {
            user.setActivated((Boolean) details.get("email_verified"));
        }
        if (details.get("email") != null) {
            user.setEmail(details.get("email").toString());
        }
        if (details.get("locale") != null) {
            String locale = details.get("locale").toString();
            String langKey = locale.substring(0, locale.indexOf("-"));
            user.setLangKey(langKey);
            user.setEmail(details.get("locale").toString());
        }

        Set<Authority> userAuthorities = new LinkedHashSet<>();

        // get groups from details
        if (details.get("groups") != null) {
            List groups = (ArrayList) details.get("groups");
            groups.forEach(group -> {
                Authority userAuthority = new Authority();
                userAuthority.setName(group.toString());
                userAuthorities.add(userAuthority);
            });
        } else {
            authentication.getAuthorities().forEach(role -> {
                Authority userAuthority = new Authority();
                userAuthority.setName(role.getAuthority());
                userAuthorities.add(userAuthority);
            });
        }

        user.setAuthorities(userAuthorities);
        UserDTO userDTO = new UserDTO(user);

        // todo: update Spring Security Authorities to match group claim from IdP
        // This might provide the solution: https://stackoverflow.com/questions/35056169/how-to-get-custom-user-info-from-oauth2-authorization-server-user-endpoint

        // save account in to sync users between IdP and JHipster's local database
        Optional<User> existingUser = userRepository.findOneByLogin(userDTO.getLogin());
        if (existingUser.isPresent()) {
            log.debug("Updating user '{}' in local database...", userDTO.getLogin());
            userService.updateUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(),
                userDTO.getLangKey(), userDTO.getImageUrl());
        } else {
            log.debug("Saving user '{}' in local database...", userDTO.getLogin());
            userRepository.save(user);
        }

        return new ResponseEntity<>(userDTO, HttpStatus.OK);
    }

    /**
     * GET  /account/sessions : get the current open sessions.
     *
     * @return the ResponseEntity with status 200 (OK) and the current open sessions in body,
     * or status 500 (Internal Server Error) if the current open sessions couldn't be retrieved
     */
    @GetMapping("/account/sessions")
    @Timed
    public ResponseEntity<List<PersistentToken>> getCurrentSessions() {
        return userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin())
            .map(user -> new ResponseEntity<>(
                persistentTokenRepository.findByUser(user),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * DELETE  /account/sessions?series={series} : invalidate an existing session.
     * <p>
     * - You can only delete your own sessions, not any other user's session
     * - If you delete one of your existing sessions, and that you are currently logged in on that session, you will
     * still be able to use that session, until you quit your browser: it does not work in real time (there is
     * no API for that), it only removes the "remember me" cookie
     * - This is also true if you invalidate your current session: you will still be able to use it until you close
     * your browser or that the session times out. But automatic login (the "remember me" cookie) will not work
     * anymore.
     * There is an API to invalidate the current session, but there is no API to check which session uses which
     * cookie.
     *
     * @param series the series of an existing session
     * @throws UnsupportedEncodingException if the series couldnt be URL decoded
     */
    @DeleteMapping("/account/sessions/{series}")
    @Timed
    public void invalidateSession(@PathVariable String series) throws UnsupportedEncodingException {
        String decodedSeries = URLDecoder.decode(series, "UTF-8");
        userRepository.findOneByLogin(SecurityUtils.getCurrentUserLogin()).ifPresent(u ->
            persistentTokenRepository.findByUser(u).stream()
                .filter(persistentToken -> StringUtils.equals(persistentToken.getSeries(), decodedSeries))
                .findAny().ifPresent(t -> persistentTokenRepository.delete(decodedSeries)));
    }
}
