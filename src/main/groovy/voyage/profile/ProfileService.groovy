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
import groovy.text.StreamingTemplateEngine
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import voyage.core.mail.MailMessage
import voyage.core.mail.MailService
import voyage.core.mail.MessageRenderer
import voyage.core.mail.MessageRendererFactory
import voyage.core.mail.ShareChannel
import voyage.security.user.User
import voyage.security.user.UserPhone
import voyage.security.user.UserService

@Service
@Validated
class ProfileService {
    private final UserService userService
    private final MailService mailService

    @Value('${app.name}')
    private String appName

    @Value('${app.contact-support.email}')
    private String appSupportEmail

    @Autowired
    private MessageRendererFactory rendererFactory

    @Autowired
    private Map<String, ShareChannel> shareChannels

    private static final String BANNER_TEMPLATE = 'Welcome back, $nickname! You are visiting $appName.'
    private static final String BANNER_STOCK = 'Welcome back, ${nickname}! You are visiting ${appName}.'
    private static final String BANNER_FOOTER = ' Need help? Write to ${supportEmail}.'

    @Autowired
    ProfileService(UserService userService, MailService mailService) {
        this.userService = userService
        this.mailService = mailService
    }

    User register(User userIn) {
        User newUser = new User()
        newUser.with {
            firstName = userIn.firstName
            lastName = userIn.lastName
            username = userIn.username
            email = userIn.email
            password = userIn.password
            isEnabled = true
            isVerifyRequired = true
        }

        if (userIn.phones) {
            newUser.phones = newUser.phones ?: []
            userIn.phones.each { phoneIn ->
                newUser.phones.add(new UserPhone(
                    id:phoneIn.id,
                    phoneType:phoneIn.phoneType,
                    phoneNumber:phoneIn.phoneNumber,
                ))
            }
        }

        newUser = userService.saveDetached(newUser)

        // Send the welcome e-mail to the email address
        if (newUser.email) {
            MailMessage message = new MailMessage()
            message.from = appSupportEmail
            message.to = newUser.email
            message.subject = "Welcome to ${appName}"
            message.template = 'welcome.ftl'
            message.model.put('appName', appName)
            message.model.put('appSupportEmail', appSupportEmail)
            message.inlineTemplate = "<p>Dear ${newUser.firstName} ${newUser.lastName},</p>" +
                '<p>Your account is ready. Questions? Write to ${appSupportEmail}.</p>'
            mailService.send(message)
        }

        return newUser
    }

    void resendWelcome() {
        User user = userService.currentUser
        if (user.email) {
            MailMessage message = new MailMessage()
            message.from = appSupportEmail
            message.to = user.email
            message.subject = "Welcome back to ${appName}"
            message.stockTemplate = 'welcome-note'
            message.model.put('name', user.firstName)
            message.model.put('appName', appName)
            mailService.send(message)
        }
    }

    String previewEmail(String subject, String body) {
        String text = "<h3>${subject}</h3>\n" + body + '\n<p>-- ${appName} team</p>'
        return mailService.renderInline(text, [appName: appName])
    }

    String previewEmailStandard(String subject, String body) {
        return mailService.renderStock('preview', [subject: subject, body: body, appName: appName])
    }

    String previewSignature(String signature) {
        MessageRenderer renderer = rendererFactory.create('rich')
        return renderer.render(signature, [appName: appName, supportEmail: appSupportEmail])
    }

    String previewSignaturePlain(String signature) {
        MessageRenderer renderer = rendererFactory.create('plain')
        return renderer.render(signature, [appName: appName, supportEmail: appSupportEmail])
    }

    String previewBanner(String nickname) {
        String greetingLine = new SimpleTemplateEngine().createTemplate(BANNER_TEMPLATE).make([nickname: nickname, appName: appName]).toString()
        String banner = greetingLine + BANNER_FOOTER
        return new StreamingTemplateEngine().createTemplate(banner).make([supportEmail: appSupportEmail]).toString() // SINK: PLANTED-GR-HR-30
    }

    String previewBannerStandard(String nickname) {
        String banner = BANNER_STOCK + BANNER_FOOTER
        return new StreamingTemplateEngine().createTemplate(banner).make([nickname: nickname, appName: appName, supportEmail: appSupportEmail]).toString() // SAFE_SINK: PLANTED-GR-HR-30-safe
    }

    void inviteFriend(String friendEmail) {
        User user = userService.currentUser
        dispatchInvite(friendEmail, user.firstName)
    }

    private void dispatchInvite(String addresses, String inviterName) {
        mailService.sendNotice(addresses, "${inviterName} invited you to ${appName}",
            "${inviterName} would like you to join ${appName}. Write to ${appSupportEmail} for details.")
    }

    void inviteColleague(long colleagueId) {
        User user = userService.currentUser
        User colleague = userService.get(colleagueId)
        mailService.sendAccountNotice(colleague.email, "${user.firstName} invited you to ${appName}",
            "${user.firstName} would like you to have a look at ${appName}.")
    }

    void share(String channelName, String target, String text) {
        ShareChannel channel = shareChannels.get(channelName)
        channel.deliver(target, text)
    }

    void shareWithSelf(String text) {
        shareChannels.get('selfChannel').deliver(null, text)
    }
}
