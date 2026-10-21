package voyage.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

@Configuration
class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder()
        Map<String, PasswordEncoder> encoderMap = ['bcrypt':bcrypt]
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder('bcrypt', encoderMap)
        delegating.setDefaultPasswordEncoderForMatches(bcrypt)
        return delegating
    }

    /**
     * Encoder for accounts imported from the previous platform whose hashes carry the {md5} prefix.
     */
    static PasswordEncoder legacyPasswordEncoder() {
        return new MessageDigestPasswordEncoder('MD5') // SINK: PLANTED-GR-HR-116
    }

    /**
     * Encoder used to re-hash an imported account once its legacy hash has been verified.
     */
    static PasswordEncoder migrationTargetEncoder() {
        return new BCryptPasswordEncoder(12) // SAFE_SINK: PLANTED-GR-HR-116-safe
    }

    /**
     * Encoder for the {pbkdf2} hashes produced by the partner directory import.
     */
    static PasswordEncoder importedAccountEncoder(String scheme) {
        switch (scheme) {
            case 'fast':
                return new Pbkdf2PasswordEncoder('', 200, 256) // SINK: PLANTED-GR-HR-120
            default:
                return strongPbkdf2Encoder()
        }
    }

    static PasswordEncoder strongPbkdf2Encoder() {
        Pbkdf2PasswordEncoder strong = new Pbkdf2PasswordEncoder('', 310000, 256) // SAFE_SINK: PLANTED-GR-HR-120-safe
        strong.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256)
        return strong
    }
}
