package com.nomcci.wallet.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nomcci.wallet.management.dto.PaymentVerificationResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

@Service
public class PaypalService {

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    @Value("${paypal.mode}")
    private String mode;

    @Value("${front_end.url}")
    private String frontendLink;

    private static final String PAYPAL_API_URL = "https://api.sandbox.paypal.com"; // Se puede cambiar a "https://api.paypal.com" en produccion

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaypalService() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }

    public String createOrder(BigDecimal amount) throws Exception {
        String accessToken = getAccessToken();
        String requestBody = "{\n" +
                "  \"intent\": \"CAPTURE\",\n" +
                "  \"purchase_units\": [\n" +
                "    {\n" +
                "      \"amount\": {\n" +
                "        \"currency_code\": \"USD\",\n" +
                "        \"value\": \"" + amount + "\"\n" +
                "      },\n" +
                "      \"description\": \"Compra de créditos\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"application_context\": {\n" +
                "    \"return_url\": \""+ frontendLink +"/payments/success\",\n" +
                "    \"cancel_url\": \""+ frontendLink +"/payments/cancel\"\n" +
                "  }\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Crea el pago
        ResponseEntity<String> response = restTemplate.exchange(PAYPAL_API_URL + "/v2/checkout/orders", HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());

            return jsonResponse.get("links").get(1).get("href").asText(); // URL para redirigir al usuario para completar el pago
        } else {
            throw new Exception("Error creating order: " + response.getStatusCode());
        }
    }



    private String getAccessToken() throws Exception {
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=client_credentials";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(PAYPAL_API_URL + "/v1/oauth2/token", HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.get("access_token").asText();
        } else {
            throw new Exception("Error obtaining access token: " + response.getStatusCode());
        }
    }

    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }



    public PaymentVerificationResponseDTO verifyPayment(String token) throws Exception {
        String accessToken = getAccessToken();

        // Llamada a PayPal para verificar el estado del pago
        ResponseEntity<String> response = restTemplate.exchange(
                PAYPAL_API_URL + "/v2/checkout/orders/" + token,
                HttpMethod.GET,
                new HttpEntity<>(createHeaders(accessToken)),
                String.class
        );


        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String status = jsonResponse.get("status").asText();

            BigDecimal amount = null;
            String currency = null;

            JsonNode purchaseUnits = jsonResponse.get("purchase_units");
            if (purchaseUnits != null && purchaseUnits.isArray()) {
                JsonNode firstUnit = purchaseUnits.get(0);

                // Extrae el monto
                JsonNode amountNode = firstUnit.get("amount");
                if (amountNode != null) {
                    String value = amountNode.get("value").asText();
                    currency = amountNode.get("currency_code").asText();
                    amount = new BigDecimal(value);
                    System.out.println("Monto extraído: " + amount + " " + currency);
                }

                // Maneja el caso APPROVED e intenta capturar el pago
                if ("APPROVED".equals(status)) {
                    JsonNode links = jsonResponse.get("links");
                    if (links != null && links.isArray()) {
                        for (JsonNode link : links) {
                            String rel = link.get("rel").asText();
                            if ("capture".equals(rel)) {
                                String captureUrl = link.get("href").asText();

                                // Intentar capturar el pago usando la URL de captura
                                String captureStatus = capturePayment(captureUrl, accessToken);
                                if ("Pago capturado correctamente".equals(captureStatus)) {
                                    return new PaymentVerificationResponseDTO("COMPLETED", amount, currency);
                                } else {
                                    throw new Exception("Error al capturar el pago. Estado de captura: " + captureStatus);
                                }
                            }
                        }
                    }
                    throw new Exception("No se encontró el enlace de captura en la respuesta.");
                }
            }

            if ("COMPLETED".equals(status)) {
                return new PaymentVerificationResponseDTO("COMPLETED", amount, currency);
            }

            throw new Exception("El pago no está aprobado ni completado. Estado: " + status);
        } else {
            String errorResponse = response.getBody();
            throw new Exception("Error al verificar el pago: " + response.getStatusCode() + " - " + errorResponse);
        }
    }

    // Método para capturar el pago usando la URL de captura
    private String capturePayment(String captureUrl, String accessToken) throws Exception {
        HttpHeaders headers = createHeaders(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                captureUrl, HttpMethod.POST, entity, String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String status = jsonResponse.get("status").asText();
            if ("COMPLETED".equals(status)) {
                return "Pago capturado correctamente";
            } else {
                throw new Exception("El pago no se completó correctamente. Estado: " + status);
            }
        } else {
            String errorResponse = response.getBody();
            throw new Exception("Error al capturar el pago: " + response.getStatusCode() + " - " + errorResponse);
        }
    }


}
