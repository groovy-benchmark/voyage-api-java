package voyage.core.mail

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import voyage.security.user.UserService

import javax.mail.internet.MimeMessage

/**
 * A way of passing a short message on to somebody; selected by bean name.
 */
interface ShareChannel {
    void deliver(String target, String text)
}

@Component('emailChannel')
class EmailShareChannel implements ShareChannel {
    private final JavaMailSender javaMailSender

    @Value('${app.contact-support.email}')
    private String from

    @Autowired
    EmailShareChannel(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender
    }

    @Override
    void deliver(String target, String text) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setFrom(from)
        helper.setTo(target) // SINK: PLANTED-GR-HR-59
        helper.setSubject('Somebody shared a message with you')
        helper.setText(text, false)
        javaMailSender.send(mimeMessage)
    }
}

@Component('selfChannel')
class SelfShareChannel implements ShareChannel {
    private final JavaMailSender javaMailSender
    private final UserService userService

    @Value('${app.contact-support.email}')
    private String from

    @Autowired
    SelfShareChannel(JavaMailSender javaMailSender, UserService userService) {
        this.javaMailSender = javaMailSender
        this.userService = userService
    }

    @Override
    void deliver(String target, String text) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage()
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false)
        helper.setFrom(from)
        helper.setTo(userService.currentUser.email) // SAFE_SINK: PLANTED-GR-HR-59-safe
        helper.setSubject('A copy of your shared message')
        helper.setText(text, false)
        javaMailSender.send(mimeMessage)
    }
}

@Component('logChannel')
class LogShareChannel implements ShareChannel {
    private static final Logger LOG = LoggerFactory.getLogger(LogShareChannel)

    @Override
    void deliver(String target, String text) {
        LOG.info('Shared message for {} ({} characters)', target, text?.length())
    }
}
