package com.rbc.fogwall.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static requests to {@code index.html} so that React Router's BrowserRouter can handle
 * client-side navigation on direct URL loads and refreshes. Also provides the Swagger UI convenience redirect.
 */
@Controller
public class SpaController {

    @RequestMapping("/dashboard/**")
    public String spa() {
        return "forward:/index.html";
    }

    /** Redirects to Swagger UI pre-pointed at the OpenAPI spec. */
    @GetMapping({"/swagger-ui", "/swagger-ui/"})
    public String swaggerUi() {
        return "redirect:/swagger-ui.html";
    }
}
