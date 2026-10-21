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
package voyage.profile

import groovy.text.SimpleTemplateEngine
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import voyage.core.mail.MailMessage
import voyage.core.mail.MailService
import voyage.security.user.User
import voyage.security.user.UserService
import voyage.security.verify.VerifyService

@RestController('apiProfileController')
@RequestMapping(['/api/v1/profiles', '/api/v1.0/profiles'])
class ProfileController {
    private final ProfileService profileService
    private final VerifyService userVerifyService
    private final UserService userService

    @Value('${app.name}')
    private String appName

    @Value('${app.contact-support.email}')
    private String supportEmail

    @Autowired
    private JavaMailSender javaMailSender

    @Autowired
    private MailService mailService

    private static final String STANDARD_GREETING = 'Hello $firstName, welcome to $appName. $greeting'

    @Autowired
    ProfileController(ProfileService profileService, VerifyService userVerifyService, UserService userService) {
        this.profileService = profileService
        this.userVerifyService = userVerifyService
        this.userService = userService
    }

    /**
     * @api {post} /v1/profile Create profile
     * @apiVersion 1.0.0
     * @apiName ProfileCreate
     * @apiGroup Profile
     *
     * @apiDescription Creates a new user profile. All parameters are required and at least 1 mobile phone must be added.
     *
     * @apiPermission none
     *
     * @apiUse AuthHeader
     *
     * @apiHeader (Response Headers) {String} location Location of the newly created resource
     *
     * @apiHeaderExample {json} New Profile Location
     * HTTP/1.1 201: Created
     * {
     *     "Location": "https://my-app/api/v1/profiles/me"
     * }
     *
     * @apiUse ProfileRequestModel
     * @apiUse ProfileSuccessModel
     * @apiUse UsernameAlreadyInUseError
     * @apiUse MobilePhoneNumberRequiredError
     * @apiUse TooManyPhonesError
     * @apiUse PhoneNumberInvalidError
     * @apiUse MailSendError
     **/
    @PostMapping('/register')
    ResponseEntity register(@RequestBody User userIn) {
        profileService.register(userIn)
        HttpHeaders headers = new HttpHeaders()
        headers.set(HttpHeaders.LOCATION, '/v1/profiles/me')
        return new ResponseEntity(headers, HttpStatus.CREATED)
    }

    @GetMapping('/me')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity myProfile() {
        User user = userService.currentUser
        return new ResponseEntity(user, HttpStatus.OK)
    }

    @PostMapping('/me/greeting-preview')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity greetingPreview(@RequestBody Map<String, String> body) {
        User user = userService.currentUser
        Map<String, Object> model = [firstName: user.firstName, appName: appName]
        String preview = new SimpleTemplateEngine().createTemplate(body.get('greeting')).make(model).toString() // SINK: PLANTED-GR-HR-26
        return new ResponseEntity([greeting: preview], HttpStatus.OK)
    }

    @PostMapping('/me/greeting-preview/standard')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity greetingPreviewStandard(@RequestBody Map<String, String> body) {
        User user = userService.currentUser
        Map<String, Object> model = [firstName: user.firstName, appName: appName, greeting: body.get('greeting')]
        String preview = new SimpleTemplateEngine().createTemplate(STANDARD_GREETING).make(model).toString() // SAFE_SINK: PLANTED-GR-HR-26-safe
        return new ResponseEntity([greeting: preview], HttpStatus.OK)
    }

    @PostMapping('/me/welcome')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity resendWelcome() {
        profileService.resendWelcome()
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/email-preview')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity emailPreview(@RequestBody Map<String, String> body) {
        String html = profileService.previewEmail(body.get('subject'), body.get('body'))
        return new ResponseEntity([html: html], HttpStatus.OK)
    }

    @PostMapping('/me/email-preview/standard')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity emailPreviewStandard(@RequestBody Map<String, String> body) {
        String html = profileService.previewEmailStandard(body.get('subject'), body.get('body'))
        return new ResponseEntity([html: html], HttpStatus.OK)
    }

    @PostMapping('/me/signature-preview')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity signaturePreview(@RequestBody Map<String, String> body) {
        return new ResponseEntity([signature: profileService.previewSignature(body.get('signature'))], HttpStatus.OK)
    }

    @PostMapping('/me/signature-preview/plain')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity signaturePreviewPlain(@RequestBody Map<String, String> body) {
        return new ResponseEntity([signature: profileService.previewSignaturePlain(body.get('signature'))], HttpStatus.OK)
    }

    @PostMapping('/me/banner-preview')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity bannerPreview(@RequestBody Map<String, String> body) {
        return new ResponseEntity([banner: profileService.previewBanner(body.get('nickname'))], HttpStatus.OK)
    }

    @PostMapping('/me/banner-preview/standard')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity bannerPreviewStandard(@RequestBody Map<String, String> body) {
        return new ResponseEntity([banner: profileService.previewBannerStandard(body.get('nickname'))], HttpStatus.OK)
    }

    @PostMapping('/me/notify-colleagues')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity notifyColleagues(@RequestBody Map<String, String> body) {
        User user = userService.currentUser
        MimeMessage message = javaMailSender.createMimeMessage()
        message.setFrom(new InternetAddress(supportEmail))
        message.setRecipients(Message.RecipientType.TO, supportEmail)
        message.addRecipients(Message.RecipientType.CC, body.get('cc')) // SINK: PLANTED-GR-HR-56
        message.setSubject("${user.firstName} ${user.lastName} shared an update")
        message.setText(body.get('note'))
        javaMailSender.send(message)
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/notify-colleagues/standard')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity notifyColleaguesStandard(@RequestBody Map<String, String> body) {
        User user = userService.currentUser
        String domain = '@' + supportEmail.substring(supportEmail.indexOf('@') + 1).toLowerCase()
        List<String> colleagues = body.get('cc').split(',').collect { String item ->
            new InternetAddress(item.trim(), true).address
        }.findAll { String address -> address.toLowerCase().endsWith(domain) }
        MimeMessage message = javaMailSender.createMimeMessage()
        message.setFrom(new InternetAddress(supportEmail))
        message.setRecipients(Message.RecipientType.TO, supportEmail)
        message.addRecipients(Message.RecipientType.CC, colleagues.join(',')) // SAFE_SINK: PLANTED-GR-HR-56-safe
        message.setSubject("${user.firstName} ${user.lastName} shared an update")
        message.setText(body.get('note'))
        javaMailSender.send(message)
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/contact')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity contactSupport(@RequestBody MailMessage contact) {
        mailService.sendContactMessage(contact)
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/contact/standard')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity contactSupportStandard(@RequestBody MailMessage contact) {
        mailService.sendContactMessageStandard(contact)
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/invite')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity invite(@RequestBody Map<String, String> body) {
        profileService.inviteFriend(body.get('email'))
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/invite/colleague')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity inviteColleague(@RequestBody Map<String, Long> body) {
        profileService.inviteColleague(body.get('colleagueId'))
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/share')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity share(@RequestBody Map<String, String> body) {
        profileService.share(body.get('channel'), body.get('target'), body.get('text'))
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/share/self')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity shareWithSelf(@RequestBody Map<String, String> body) {
        profileService.shareWithSelf(body.get('text'))
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/campaign-preview')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity campaignPreview(@RequestBody Map<String, String> headers) {
        mailService.sendCampaign(headers, 'Campaign preview')
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PostMapping('/me/campaign-preview/standard')
    @PreAuthorize("hasAuthority('api.profiles.me')")
    ResponseEntity campaignPreviewStandard(@RequestBody Map<String, String> headers) {
        mailService.sendCampaignStandard(headers, 'Campaign preview')
        return new ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
