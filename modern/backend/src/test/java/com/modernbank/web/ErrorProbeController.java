package com.modernbank.web;

import com.modernbank.common.BadRequestException;
import com.modernbank.common.BusinessRuleException;
import com.modernbank.common.NotFoundException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that raise each exception type so the error contract can be verified. */
@RestController
public class ErrorProbeController {

    @GetMapping("/api/test/bad-request")
    public void badRequest() {
        throw new BadRequestException("Amount must be a valid number.");
    }

    @GetMapping("/api/test/not-found")
    public void notFound() {
        throw new NotFoundException("Account not found: 1");
    }

    @GetMapping("/api/test/business-rule")
    public void businessRule() {
        throw new BusinessRuleException("Account is FROZEN.");
    }

    @GetMapping("/api/test/boom")
    public void boom() {
        throw new IllegalStateException("secret internal detail at com.modernbank.Foo.bar(Foo.java:42)");
    }

    @GetMapping("/api/test/param")
    public String param(@RequestParam("id") long id) {
        return String.valueOf(id);
    }
}
