package com.nomcci.wallet.management.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final PrivateKey privateKey;

    @Value("${issuer.url}")
    private String issuerUrl;

    public JwtUtil() {
        RsaKeyUtil.generateKeyPair(); // Genera las claves si no existen
        this.privateKey = RsaKeyUtil.loadPrivateKey();
    }

    /**
     * Genera un token JWT para el servicio Wallet.
     *
     * @return Token JWT.
     */
    public String generateServiceToken() {
        try {
            long expirationTime = 36000;
            Date now = new Date();
            Date expirationDate = new Date(now.getTime() + expirationTime);

            String jwt= Jwts.builder()
                    .setSubject("service-token")
                    .setAudience("wallet-service")
                    .setIssuedAt(now)
                    .setExpiration(expirationDate)
                    .setIssuer(issuerUrl)
                    .claim("scope", "SCOPE_WALLET_ACCESS")
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();
            System.out.println(jwt);
            return jwt;
        } catch (JwtException e) {
            throw new RuntimeException("Error al generar el token JWT para el servicio", e);
        }
    }

}