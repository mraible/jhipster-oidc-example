package com.okta.developer.web.rest;

import com.codahale.metrics.annotation.Timed;

import com.okta.developer.domain.Authority;
import com.okta.developer.domain.User;
import com.okta.developer.repository.UserRepository;
import com.okta.developer.service.UserService;
import com.okta.developer.service.dto.UserDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Instant;
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

    public AccountResource(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
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
        if (principal != null) {
            if (principal instanceof OAuth2Authentication) {
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
                }

                Set<Authority> userAuthorities = new LinkedHashSet<>();

                // get groups from details
                if (details.get("groups") != null) {
                    List groups = (ArrayList) details.get("groups");
                    groups.forEach(group -> {
                        // Ignore Okta's Everyone group, or add it to Liquibase's authorities.csv
                        if (!String.valueOf(group).equalsIgnoreCase("everyone")) {
                            Authority userAuthority = new Authority();
                            userAuthority.setName(group.toString());
                            userAuthorities.add(userAuthority);
                        }
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

                // convert Authorities to GrantedAuthorities
                Set<GrantedAuthority> grantedAuthorities = new LinkedHashSet<>();
                userAuthorities.forEach(authority -> {
                    grantedAuthorities.add(new SimpleGrantedAuthority(authority.getName()));
                });

                // Update Spring Security Authorities to match groups claim from IdP
                UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                    principal, "N/A", grantedAuthorities);
                token.setDetails(details);
                authentication = new OAuth2Authentication(authentication.getOAuth2Request(), token);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // save account in to sync users between IdP and JHipster's local database
                Optional<User> existingUser = userRepository.findOneByLogin(userDTO.getLogin());
                if (existingUser.isPresent()) {
                    // if IdP sends last updated information, use it to determine if an update should happen
                    if (details.get("updated_at") != null) {
                        Instant dbModifiedDate = existingUser.get().getLastModifiedDate();
                        Instant idpModifiedDate = new Date(Long.valueOf((Integer) details.get("updated_at"))).toInstant();
                        if (idpModifiedDate.isAfter(dbModifiedDate)) {
                            log.debug("Updating user '{}' in local database...", userDTO.getLogin());
                            userService.updateUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(),
                                userDTO.getLangKey(), userDTO.getImageUrl());
                        }
                        // no last updated info, blindly update
                    } else {
                        log.debug("Updating user '{}' in local database...", userDTO.getLogin());
                        userService.updateUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(),
                            userDTO.getLangKey(), userDTO.getImageUrl());
                    }
                } else {
                    log.debug("Saving user '{}' in local database...", userDTO.getLogin());
                    userRepository.save(user);
                }
                return new ResponseEntity<>(userDTO, HttpStatus.OK);
            } else {
                // Allow Spring Security Test to be used to mock users in the database
                return Optional.ofNullable(userService.getUserWithAuthorities())
                    .map(user -> new ResponseEntity<>(new UserDTO(user), HttpStatus.OK))
                    .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        } else {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
