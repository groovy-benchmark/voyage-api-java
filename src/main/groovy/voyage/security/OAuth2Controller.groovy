/*
 * Copyright 2018 Lighthouse Software, Inc.   http://www.LighthouseSoftware.com
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package voyage.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.SessionAttributes
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.HtmlUtils
import voyage.security.client.Client
import voyage.security.client.ClientService

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

@Controller
@SessionAttributes('authorizationRequest')
class OAuth2Controller {
    private static final String DEFAULT_LANDING = '/'
    private static final Map<String, String> LANDING_PAGES = [home: '/', profile: '/api/v1/profiles/me', docs: '/api/docs'].asImmutable()
    private static final Map<String, RedirectResolver> RESOLVERS = [legacy: new LegacyRedirectResolver(), strict: new StrictRedirectResolver()].asImmutable()

    @Autowired
    private ClientService clientService

    @RequestMapping('/oauth/confirm_access')
    ModelAndView getAccessConfirmation(Map<String, Object> model) throws Exception {
        return new ModelAndView('oauth-confirm-access', model)
    }

    @RequestMapping('/oauth/error')
    ModelAndView handleError(HttpServletRequest request) {
        Map<String, Object> model = [:]
        Object error = request.getAttribute('error')
        // The error summary may contain malicious user input, it needs to be escaped to prevent XSS
        String errorSummary
        if (error instanceof OAuth2Exception) {
            OAuth2Exception oauthError = (OAuth2Exception)error
            errorSummary = HtmlUtils.htmlEscape(oauthError.summary)
        } else if (error instanceof Exception) {
            Exception oauthError = (Exception)error
            errorSummary = oauthError.message
        } else {
            errorSummary = error.toString()
        }
        model.put('errorSummary', errorSummary)
        return new ModelAndView('oauth-error', model)
    }

    /**
     * Landing page for a client that was denied access; the authorization server appends the error description.
     */
    @GetMapping(value = '/oauth/denied', produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String accessDenied(@RequestParam('error_description') String description) {
        return describeDenial(description)
    }

    @GetMapping(value = '/oauth/denied/encoded', produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String accessDeniedEncoded(@RequestParam('error_description') String description) {
        return describeEncodedDenial(description)
    }

    private static String describeDenial(String description) {
        return "<html><body><h2>Access denied</h2><p>${description}</p></body></html>".toString() // SINK: PLANTED-GR-HR-305
    }

    private static String describeEncodedDenial(String description) {
        return "<html><body><h2>Access denied</h2><p>${HtmlUtils.htmlEscape(description)}</p></body></html>".toString() // SAFE_SINK: PLANTED-GR-HR-305-safe
    }

    /**
     * Sends the user back to the page they were on before the sign-in / consent screens.
     */
    @GetMapping('/oauth/return')
    String returnToPage(@RequestParam('to') String returnTo) {
        return 'redirect:' + returnTo // SINK: PLANTED-GR-HR-201
    }

    @GetMapping('/oauth/return/local')
    String returnToLocalPage(@RequestParam('to') String returnTo) {
        String path = (returnTo ==~ /\/(?![\/\\])[A-Za-z0-9._~\/?=&%-]*/) ? returnTo : DEFAULT_LANDING
        return 'redirect:' + path // SAFE_SINK: PLANTED-GR-HR-201-safe
    }

    /**
     * "Back" link used by the consent and error screens, returns to the referring page.
     */
    @GetMapping('/oauth/back')
    void back(HttpServletRequest request, HttpServletResponse response) {
        String previous = request.getHeader('Referer') ?: DEFAULT_LANDING
        response.sendRedirect(previous) // SINK: PLANTED-GR-HR-202
    }

    @GetMapping('/oauth/back/local')
    void backLocal(HttpServletRequest request, HttpServletResponse response) {
        String previous = request.getHeader('Referer')
        String target = DEFAULT_LANDING
        if (previous) {
            try {
                URI parsed = new URI(previous)
                if (parsed.host == request.serverName && parsed.rawPath?.startsWith('/') && !parsed.rawPath.startsWith('//')) {
                    target = parsed.rawPath + (parsed.rawQuery ? '?' + parsed.rawQuery : '')
                }
            } catch (URISyntaxException ignored) {
                target = DEFAULT_LANDING
            }
        }
        response.sendRedirect(target) // SAFE_SINK: PLANTED-GR-HR-202-safe
    }

    /**
     * Completes an interactive hand-off by returning the user agent to one of the client's registered redirect URIs.
     */
    @GetMapping('/oauth/continue')
    ResponseEntity<Void> continueToClient(@RequestParam('client_id') String clientId, @RequestParam('redirect_uri') String redirectUri) {
        Client client = clientService.findByClientIdentifier(clientId)
        boolean registered = client?.clientRedirects?.any { redirectUri.startsWith(it.redirectUri) }
        if (!registered) {
            return new ResponseEntity<Void>(HttpStatus.BAD_REQUEST)
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUri)).build() // SINK: PLANTED-GR-HR-203
    }

    @GetMapping('/oauth/continue/verified')
    ResponseEntity<Void> continueToVerifiedClient(@RequestParam('client_id') String clientId, @RequestParam('redirect_uri') String redirectUri) {
        Client client = clientService.findByClientIdentifier(clientId)
        String registered = client?.clientRedirects?.collect { it.redirectUri }?.find { it == redirectUri }
        if (!registered) {
            return new ResponseEntity<Void>(HttpStatus.BAD_REQUEST)
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(registered)).build() // SAFE_SINK: PLANTED-GR-HR-203-safe
    }

    /**
     * Post-consent hand-off. The resolver is chosen by the "mode" value the caller sends; older integrations still
     * send "legacy".
     */
    @GetMapping('/oauth/handoff')
    void handoff(@RequestParam('next') String next, @RequestParam(value = 'mode', defaultValue = 'legacy') String mode, HttpServletResponse response) {
        RedirectResolver resolver = RESOLVERS.get(mode) ?: RESOLVERS.get('legacy')
        String target = resolver.resolve(next)
        response.setStatus(HttpServletResponse.SC_FOUND)
        response.setHeader('Location', target) // SINK: PLANTED-GR-HR-204
    }

    @GetMapping('/oauth/handoff/strict')
    void handoffStrict(@RequestParam('next') String next, HttpServletResponse response) {
        RedirectResolver resolver = RESOLVERS.get('strict')
        String target = resolver.resolve(next)
        response.setStatus(HttpServletResponse.SC_FOUND)
        response.setHeader('Location', target) // SAFE_SINK: PLANTED-GR-HR-204-safe
    }

    /**
     * Remembers where the user wanted to go before being sent through the sign-in flow.
     */
    @PostMapping('/oauth/login-return')
    ResponseEntity<Void> rememberReturn(@RequestParam('to') String returnTo, @RequestParam(value = 'page', defaultValue = 'home') String page, HttpSession session) {
        session.setAttribute('returnTo', returnTo)
        session.setAttribute('returnKey', page)
        return new ResponseEntity<Void>(HttpStatus.NO_CONTENT)
    }

    @GetMapping('/oauth/resume')
    ModelAndView resume(HttpSession session) {
        String saved = (String)session.getAttribute('returnTo') ?: DEFAULT_LANDING
        return new ModelAndView(new RedirectView(saved)) // SINK: PLANTED-GR-HR-205
    }

    @GetMapping('/oauth/resume/page')
    ModelAndView resumePage(HttpSession session) {
        String landing = LANDING_PAGES.get((String)session.getAttribute('returnKey')) ?: DEFAULT_LANDING
        return new ModelAndView(new RedirectView(landing)) // SAFE_SINK: PLANTED-GR-HR-205-safe
    }
}

interface RedirectResolver {
    String resolve(String next)
}

/** Original behaviour kept for integrations that pass site-relative paths. */
class LegacyRedirectResolver implements RedirectResolver {
    @Override
    String resolve(String next) {
        return next.startsWith('/') ? next : '/'
    }
}

class StrictRedirectResolver implements RedirectResolver {
    @Override
    String resolve(String next) {
        return (next ==~ /\/(?![\/\\])[A-Za-z0-9._~\/?=&%-]*/) ? next : '/'
    }
}
