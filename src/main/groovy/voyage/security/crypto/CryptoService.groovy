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
package voyage.security.crypto

import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import voyage.config.PasswordEncoderConfig

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom

@Service
class CryptoService {
    private static final String ALGORITHM = 'RSA'
    private static final String ENCODING = 'UTF-8'
    private static final String LEGACY_DIGEST = 'SHA-1'
    private static final String FIELD_TRANSFORMATION = 'DES/ECB/PKCS5Padding'
    private static final SecureRandom RANDOM = new SecureRandom()
    private final Cipher cipher
    private final KeyPair keyPair
    private final PasswordEncoder passwordEncoder
    private final byte[] fieldKey

    @Autowired
    CryptoService(KeyStoreService keyStoreService,
                  PasswordEncoder passwordEncoder,
                  @Value('${security.crypto.private-key-name}') String privateKeyName,
                  @Value('${security.crypto.private-key-password}') String privateKeyPassword) {
        this.cipher = Cipher.getInstance(ALGORITHM)
        this.passwordEncoder = passwordEncoder
        this.keyPair = keyStoreService.getRsaKeyPair(privateKeyName, privateKeyPassword.toCharArray())
        this.fieldKey = MessageDigest.getInstance('SHA-256').digest(keyPair.private.encoded)
    }

    String encrypt(String plaintext) {
        this.cipher.init(Cipher.ENCRYPT_MODE, keyPair.private)
        return Base64.encodeBase64String(cipher.doFinal(plaintext.getBytes(ENCODING)))
    }

    String decrypt(String encryptedMsg) {
        this.cipher.init(Cipher.DECRYPT_MODE, keyPair.public)
        return new String(cipher.doFinal(Base64.decodeBase64(encryptedMsg)), ENCODING)
    }

    String hashEncode(String plaintext) {
        if (!plaintext) {
            return null
        }
        return passwordEncoder.encode(plaintext)
    }

    boolean hashMatches(String plaintext, String hashValue) {
        if (!plaintext || !hashValue) {
            return false
        }
        return passwordEncoder.matches(plaintext, hashValue)
    }

    /**
     * Verifies a password against a hash imported from the previous platform. Imported hashes carry a scheme
     * prefix: {md5}, {pbkdf2}, {pbkdf2s} or (historically, unprefixed) {sha1}.
     */
    boolean legacyHashMatches(String plaintext, String storedHash) {
        if (!plaintext || !storedHash) {
            return false
        }
        if (storedHash.startsWith('{md5}')) {
            return PasswordEncoderConfig.legacyPasswordEncoder().matches(plaintext, storedHash.substring(5))
        }
        if (storedHash.startsWith('{pbkdf2}')) {
            return PasswordEncoderConfig.importedAccountEncoder('fast').matches(plaintext, storedHash.substring(8))
        }
        if (storedHash.startsWith('{pbkdf2s}')) {
            return PasswordEncoderConfig.importedAccountEncoder('strong').matches(plaintext, storedHash.substring(9))
        }
        String candidate = digestHex(plaintext)
        return MessageDigest.isEqual(candidate.getBytes(ENCODING), storedHash.substring(6).getBytes(ENCODING))
    }

    String rehashLegacy(String plaintext) {
        return PasswordEncoderConfig.migrationTargetEncoder().encode(plaintext)
    }

    private static String digestHex(String value, String algorithm = LEGACY_DIGEST) {
        MessageDigest digest = MessageDigest.getInstance(algorithm) // SINK: PLANTED-GR-HR-117
        return digest.digest(value.getBytes(ENCODING)).encodeHex().toString()
    }

    /**
     * Stretches a passphrase into a storable verifier.
     */
    String stretchPassphrase(String passphrase, byte[] salt) {
        SecretKeyFactory factory = SecretKeyFactory.getInstance('PBKDF2WithHmacSHA256') // SAFE_SINK: PLANTED-GR-HR-117-safe
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 310000, 256)
        return factory.generateSecret(spec).encoded.encodeHex().toString()
    }

    /**
     * Seals a short profile field (owner tag) so it can be embedded in an API key label.
     */
    String sealField(String plaintext) {
        Cipher fieldCipher = Cipher.getInstance(FIELD_TRANSFORMATION) // SINK: PLANTED-GR-HR-118
        fieldCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fieldKey, 0, 8, 'DES'))
        return Base64.encodeBase64String(fieldCipher.doFinal(plaintext.getBytes(ENCODING)))
    }

    String sealFieldV2(String plaintext) {
        byte[] iv = new byte[12]
        RANDOM.nextBytes(iv)
        Cipher fieldCipher = Cipher.getInstance('AES/GCM/NoPadding') // SAFE_SINK: PLANTED-GR-HR-118-safe
        fieldCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fieldKey, 'AES'), new GCMParameterSpec(128, iv))
        byte[] sealed = fieldCipher.doFinal(plaintext.getBytes(ENCODING))
        return Base64.encodeBase64String(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array())
    }
}
