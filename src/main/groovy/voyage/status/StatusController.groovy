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
package voyage.status

import groovy.text.SimpleTemplateEngine
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils

import java.text.SimpleDateFormat

@RestController
@RequestMapping(['/api/status'])
class StatusController {
    private static final String NOTICE_PAGE = '''<html><body><h1>Service status</h1><p class="notice">${notice}</p><p>${datetime}</p></body></html>'''
    private static final Map<String, StatusRenderer> RENDERERS = [inline: new InlineStatusRenderer(), escaped: new EscapedStatusRenderer()].asImmutable()

    /**
     * @api {get} /status Status
     * @apiVersion 1.0.0
     * @apiName Status
     * @apiGroup Status
     *
     * @apiPermission none
     *
     * @apiSuccess {String} status
     * @apiSuccess {String} datetime
     *
     * @apiSuccessExample Success-Response:
     *   HTTP/1.1 200 OK
     *   [
     *       {
     *           "status": "alive",
     *           "datetime": "2016-12-23 17:55:55 UTC",
     *       }
     *   ]
     **/
    @GetMapping
    ResponseEntity list() {
        Map<String, String> response = [status:'alive', datetime:currentDate]
        return new ResponseEntity(response, HttpStatus.OK)
    }

    /**
     * HTML status badge for a single service, embedded by the uptime widgets on the docs site.
     */
    @GetMapping(params = 'service', produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> serviceBadge(@RequestParam('service') String service) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<div class=\"status-badge\"><b>${service}</b>: alive</div>".toString()) // SINK: PLANTED-GR-HR-301
    }

    @GetMapping(params = 'component', produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> componentBadge(@RequestParam('component') String component) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<div class=\"status-badge\"><b>${HtmlUtils.htmlEscape(component)}</b>: alive</div>".toString()) // SAFE_SINK: PLANTED-GR-HR-301-safe
    }

    /**
     * Stand-alone status page with an operator notice (e.g. maintenance window text) shown above the timestamp.
     */
    @GetMapping(params = 'notice', produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> noticePage(@RequestParam('notice') String notice) {
        Map<String, Object> model = [notice:notice, datetime:currentDate]
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new SimpleTemplateEngine().createTemplate(NOTICE_PAGE).make(model).toString()) // SINK: PLANTED-GR-HR-302
    }

    @GetMapping(params = 'message', produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> messagePage(@RequestParam('message') String message) {
        Map<String, Object> model = [notice:HtmlUtils.htmlEscape(message), datetime:currentDate]
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new SimpleTemplateEngine().createTemplate(NOTICE_PAGE).make(model).toString()) // SAFE_SINK: PLANTED-GR-HR-302-safe
    }

    /**
     * Status widget; the label markup is produced by the renderer selected with the optional 'fmt' parameter.
     */
    @GetMapping(params = 'widget', produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> widget(@RequestParam('widget') String widget, @RequestParam(value = 'fmt', defaultValue = 'inline') String fmt) {
        StatusRenderer renderer = RENDERERS.get(fmt) ?: RENDERERS.get('inline')
        String html = '<div class="status-widget">' + renderer.render(widget) + ' is alive</div>'
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html)
    }

    private static String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.default).format(new Date())
    }
}

interface StatusRenderer {
    String render(String label)
}

class InlineStatusRenderer implements StatusRenderer {
    @Override
    String render(String label) {
        return "<span class=\"status-label\">${label}</span>".toString() // SINK: PLANTED-GR-HR-303
    }
}

class EscapedStatusRenderer implements StatusRenderer {
    @Override
    String render(String label) {
        return "<span class=\"status-label\">${HtmlUtils.htmlEscape(label)}</span>".toString() // SAFE_SINK: PLANTED-GR-HR-303-safe
    }
}
