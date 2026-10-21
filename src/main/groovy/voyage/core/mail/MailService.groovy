/*
 * Copyright 2017 Lighthouse Software, Inc.   http://www.LighthouseSoftware.com
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
package voyage.core.mail

import freemarker.cache.StringTemplateLoader
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils

import javax.mail.internet.MimeMessage

@Service
class MailService {
    private static final Logger LOG = LoggerFactory.getLogger(MailService)

    @Value('${app.contact-support.email}')
    private String from

    @Value('${spring.mail.overrideAddress}')
    private String overrideAddress

    private static final Set<String> CAMPAIGN_HEADERS = ['X-Campaign', 'X-Campaign-Source'] as Set

    private static final Map<String, String> STOCK_TEMPLATES = [
        'welcome-note': 'Hello ${name}, thanks for joining ${appName}.',
        'preview'     : '<h3>${subject}</h3>\n${body}\n<p>-- ${appName} team</p>',
    ]

    private final JavaMailSender javaMailSender
    private final Configuration freeMarkerConfig

    @Autowired
    MailService(JavaMailSender javaMailSender, Configuration freeMarkerConfig) {
        this.javaMailSender = javaMailSender
        this.freeMarkerConfig = freeMarkerConfig
    }

    void send(MailMessage mailMessage) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true)
        mailMessage.from = mailMessage.from ?: from
        mimeMessageHelper.setFrom(mailMessage.from)
        if (overrideAddress) {
            mailMessage.to = overrideAddress
        }
        mimeMessageHelper.setTo(mailMessage.to)
        mimeMessageHelper.setSubject(mailMessage.subject)

        if (mailMessage.template) {
            String text = getContentFromTemplate(mailMessage.model, mailMessage.template)
            if (mailMessage.inlineTemplate) {
                text += getContentFromInlineTemplate(mailMessage.model, mailMessage.inlineTemplate)
            }
            mimeMessageHelper.setText(text, true)
        } else if (mailMessage.stockTemplate) {
            String text = getContentFromStockTemplate(mailMessage.model, mailMessage.stockTemplate)
            mimeMessageHelper.setText(text, true)
        } else if (mailMessage.text) {
            mimeMessageHelper.setText(mailMessage.text, true)
        }

        try {
            if (LOG.debugEnabled) {
                LOG.debug('Sending e-mail to ' + mailMessage.to)
            }
            javaMailSender.send(mimeMessageHelper.mimeMessage)
        } catch (MailException ignore) {
            throw new MailSendException()
        }
    }

    private String getContentFromTemplate(Map<String, Object> model, String template) {
        try {
            return FreeMarkerTemplateUtils.processTemplateIntoString(freeMarkerConfig.getTemplate(template), model)
        } catch (IOException e) {
            LOG.error('Template {} was not found or could not be read, exception : {}', template, e.message)
            throw new MailSendException()
        } catch (TemplateException e) {
            LOG.error('Template {} rendering failed, exception : {}', template, e.message)
            throw new MailSendException()
        }
    }

    private String getContentFromInlineTemplate(Map<String, Object> model, String templateText) {
        StringTemplateLoader loader = new StringTemplateLoader()
        loader.putTemplate('inline', templateText) // SINK: PLANTED-GR-HR-28
        Configuration inlineConfig = new Configuration(freeMarkerConfig.incompatibleImprovements)
        inlineConfig.templateLoader = loader
        try {
            return FreeMarkerTemplateUtils.processTemplateIntoString(inlineConfig.getTemplate('inline'), model)
        } catch (IOException e) {
            LOG.error('Inline template could not be read, exception : {}', e.message)
            throw new MailSendException()
        } catch (TemplateException e) {
            LOG.error('Inline template rendering failed, exception : {}', e.message)
            throw new MailSendException()
        }
    }

    private String getContentFromStockTemplate(Map<String, Object> model, String templateKey) {
        StringTemplateLoader loader = new StringTemplateLoader()
        loader.putTemplate('stock', STOCK_TEMPLATES.get(templateKey)) // SAFE_SINK: PLANTED-GR-HR-28-safe
        Configuration stockConfig = new Configuration(freeMarkerConfig.incompatibleImprovements)
        stockConfig.templateLoader = loader
        try {
            return FreeMarkerTemplateUtils.processTemplateIntoString(stockConfig.getTemplate('stock'), model)
        } catch (IOException e) {
            LOG.error('Stock template {} could not be read, exception : {}', templateKey, e.message)
            throw new MailSendException()
        } catch (TemplateException e) {
            LOG.error('Stock template {} rendering failed, exception : {}', templateKey, e.message)
            throw new MailSendException()
        }
    }

    /**
     * Renders caller supplied template text with the mail template engine, e.g. for a preview of a custom message.
     */
    String renderInline(String templateText, Map<String, Object> model) {
        try {
            Template template = new Template('inline', new StringReader(templateText), freeMarkerConfig) // SINK: PLANTED-GR-HR-27
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model)
        } catch (IOException e) {
            LOG.error('Inline template could not be read, exception : {}', e.message)
            throw new MailSendException()
        } catch (TemplateException e) {
            LOG.error('Inline template rendering failed, exception : {}', e.message)
            throw new MailSendException()
        }
    }

    /**
     * Renders one of the built-in message templates, selected by key.
     */
    String renderStock(String templateKey, Map<String, Object> model) {
        String source = STOCK_TEMPLATES.get(templateKey)
        if (source == null) {
            throw new MailSendException()
        }
        try {
            Template template = new Template(templateKey, new StringReader(source), freeMarkerConfig) // SAFE_SINK: PLANTED-GR-HR-27-safe
            return FreeMarkerTemplateUtils.processTemplateIntoString(template, model)
        } catch (IOException e) {
            LOG.error('Stock template {} could not be read, exception : {}', templateKey, e.message)
            throw new MailSendException()
        } catch (TemplateException e) {
            LOG.error('Stock template {} rendering failed, exception : {}', templateKey, e.message)
            throw new MailSendException()
        }
    }

    /**
     * Forwards a contact-form message to the support mailbox, sent on behalf of the submitting user.
     */
    void sendContactMessage(MailMessage contact) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setFrom(contact.from) // SINK: PLANTED-GR-HR-57
        helper.setTo(overrideAddress ?: from)
        helper.setSubject(contact.subject)
        helper.setText(contact.text, false)
        javaMailSender.send(mimeMessage)
    }

    /**
     * Forwards a contact-form message to the support mailbox; the sender address is only quoted in the text.
     */
    void sendContactMessageStandard(MailMessage contact) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setFrom(from) // SAFE_SINK: PLANTED-GR-HR-57-safe
        helper.setTo(overrideAddress ?: from)
        helper.setSubject(contact.subject)
        helper.setText("Message from ${contact.from}:\n\n${contact.text}", false)
        javaMailSender.send(mimeMessage)
    }

    /**
     * Sends a short plain-text notice to a list of comma separated recipients.
     */
    void sendNotice(String recipients, String subject, String text) {
        SimpleMailMessage notice = new SimpleMailMessage()
        notice.setFrom(from)
        notice.setTo(recipients.split(',')) // SINK: PLANTED-GR-HR-58
        notice.setSubject(subject)
        notice.setText(text)
        javaMailSender.send(notice)
    }

    /**
     * Sends a short plain-text notice to the address on record for an account.
     */
    void sendAccountNotice(String accountEmail, String subject, String text) {
        SimpleMailMessage notice = new SimpleMailMessage()
        notice.setFrom(from)
        notice.setTo(accountEmail) // SAFE_SINK: PLANTED-GR-HR-58-safe
        notice.setSubject(subject)
        notice.setText(text)
        javaMailSender.send(notice)
    }

    /**
     * Sends a campaign preview to the support mailbox with the caller supplied custom headers.
     */
    void sendCampaign(Map<String, String> headers, String text) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setFrom(from)
        helper.setTo(overrideAddress ?: from)
        helper.setSubject('Campaign preview')
        helper.setText(text, false)
        headers.each { String name, String value ->
            mimeMessage.setHeader(name, value) // SINK: PLANTED-GR-HR-60
        }
        javaMailSender.send(mimeMessage)
    }

    /**
     * Sends a campaign preview to the support mailbox; only the known campaign headers are accepted.
     */
    void sendCampaignStandard(Map<String, String> headers, String text) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setFrom(from)
        helper.setTo(overrideAddress ?: from)
        helper.setSubject('Campaign preview')
        helper.setText(text, false)
        headers.each { String name, String value ->
            if (CAMPAIGN_HEADERS.contains(name)) {
                mimeMessage.setHeader(name, value.replaceAll(/[\r\n]+/, ' ')) // SAFE_SINK: PLANTED-GR-HR-60-safe
            }
        }
        javaMailSender.send(mimeMessage)
    }
}
