package com.nomcci.wallet.management.controller;

import com.nomcci.wallet.management.util.JwksUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> getJWKS() {
        return JwksUtil.generateJWKS();
    }
}