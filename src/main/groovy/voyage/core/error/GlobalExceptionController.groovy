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
package voyage.core.error

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.WebRequest
import org.springframework.web.util.HtmlUtils

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
class GlobalExceptionController implements ErrorController {
    private final ErrorAttributes errorAttributes
    private final GlobalExceptionHandler globalExceptionHandler

    String errorPath = '/error' // Overrides ErrorController.getErrorPath()

    @Autowired
    GlobalExceptionController(ErrorAttributes errorAttributes, GlobalExceptionHandler globalExceptionHandler) {
        this.errorAttributes = errorAttributes
        this.globalExceptionHandler = globalExceptionHandler
    }

    @RequestMapping(value = '/error')
    ResponseEntity<?> handleError(HttpServletRequest request, WebRequest webRequest, HttpServletResponse response) {
        // Browsers that followed a link carrying a 'ref' parameter get a human-readable page instead of the JSON body
        String ref = request.getParameter('ref')
        String accept = request.getHeader('Accept')
        if (ref != null && accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
            Map<String, Object> page = [status:response.status, ref:ref]
            if ('XMLHttpRequest' == request.getHeader('X-Requested-With')) {
                // Script-driven callers embed the page as a fragment
                return ResponseEntity.status(response.status).contentType(MediaType.TEXT_HTML).body(buildErrorFragment(page))
            }
            return ResponseEntity.status(response.status).contentType(MediaType.TEXT_HTML).body(buildErrorPage(page))
        }

        // Handle AppExceptions by the definition embedded in the exception
        Exception exception = (Exception)request.getAttribute('javax.servlet.error.exception')
        if (exception instanceof AppException) {
            return globalExceptionHandler.handle((AppException)exception)
        }

        // Handle unknown exceptions based on the error details given
        Map errorMap = getErrorAttributes(webRequest, false)
        String errorCode = ErrorUtils.getErrorCode((int)errorMap.status)
        String errorMessage = "${errorMap.status} ${errorMap.error}. ${errorMap.message}"
        ErrorResponse errorResponse = new ErrorResponse(
            error:errorCode,
            errorDescription:errorMessage,
        )
        return new ResponseEntity([errorResponse], HttpStatus.valueOf(response.status))
    }

    private static String buildErrorPage(Map<String, Object> page) {
        return "<html><body><h1>Error ${page.status}</h1><p>The link you followed (${page.ref}) could not be served.</p></body></html>".toString() // SINK: PLANTED-GR-HR-304
    }

    private static String buildErrorFragment(Map<String, Object> page) {
        return "<div class=\"error\"><h1>Error ${page.status}</h1><p>The link you followed (${HtmlUtils.htmlEscape((String)page.ref)}) could not be served.</p></div>".toString() // SAFE_SINK: PLANTED-GR-HR-304-safe
    }

    private Map<String, Object> getErrorAttributes(WebRequest request, boolean includeStackTrace = false) {
        ErrorAttributeOptions errorAttributeOptions = includeStackTrace ?
                ErrorAttributeOptions.of(ErrorAttributeOptions.Include.STACK_TRACE) : ErrorAttributeOptions.defaults()
        return errorAttributes.getErrorAttributes(request, errorAttributeOptions)
    }
}
