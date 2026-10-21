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
package voyage.core.mail

import groovy.text.GStringTemplateEngine
import org.springframework.stereotype.Component

/**
 * Turns user-facing message text (e.g. an e-mail signature) into final output.
 */
interface MessageRenderer {
    String render(String text, Map<String, Object> model)
}

/**
 * Rich rendering: the message text may use ${placeholders}.
 */
class TemplateMessageRenderer implements MessageRenderer {
    @Override
    String render(String text, Map<String, Object> model) {
        return new GStringTemplateEngine().createTemplate(text).make(model).toString() // SINK: PLANTED-GR-HR-29
    }
}

/**
 * Plain rendering: only the known {{placeholders}} are replaced.
 */
class PlainMessageRenderer implements MessageRenderer {
    @Override
    String render(String text, Map<String, Object> model) {
        String result = text
        model.each { key, value ->
            result = result.replace('{{' + key + '}}', String.valueOf(value)) // SAFE_SINK: PLANTED-GR-HR-29-safe
        }
        return result
    }
}

@Component
class MessageRendererFactory {
    MessageRenderer create(String kind) {
        return kind == 'rich' ? new TemplateMessageRenderer() : new PlainMessageRenderer()
    }
}
