package com.nomcci.wallet.management.controller;

import com.nomcci.wallet.management.dto.PaymentVerificationResponseDTO;
import com.nomcci.wallet.management.service.PaypalService;
import com.nomcci.wallet.management.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallet/payments")
public class PaymentController {

    @Autowired
    private PaypalService paypalService;

    @Autowired
    private WalletService walletService;

    /**
     * Endpoint para crear ordenes de paypal con un valor personalizado
     * @param amount Valor personalizado a pagar
     * @return URL de aprobacion o error
     */
    @PostMapping("/create-order")
    public ResponseEntity<String> createOrder(@RequestParam BigDecimal amount) {
        try {
            String approvalUrl = paypalService.createOrder(amount);
            return ResponseEntity.ok(approvalUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear la orden: " + e.getMessage());
        }
    }


    /**
     *Endpoint para verificar el proceso de pago y depositar el dinero en la cuenta del usuario
     * @param token Token generado por paypal
     * @return Mensaje exitoso o error
     */
    @GetMapping("/success")
    public ResponseEntity<String> success(@RequestParam("token") String token) {
        try {

            PaymentVerificationResponseDTO paymentStatus = paypalService.verifyPayment(token);
            System.out.println("El pago se ha procesado: "+paymentStatus);
            return switch (paymentStatus.getStatus()) {
                case "COMPLETED" ->
                        ResponseEntity.ok("Pago completado exitosamente. Depositado: "+ walletService.deposit(paymentStatus.getAmount()));
                case "APPROVED" -> ResponseEntity.ok("El pago fue aprobado, pero no necesita captura adicional.");
                default -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("El pago no se completó correctamente. Estado recibido: " + paymentStatus);
            };
        } catch (Exception e) {
            // Registrar el error para depuración
            e.printStackTrace();

            // Responder con el mensaje completo del error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al verificar el pago. Mensaje de error: " + e.getMessage());
        }
    }



    // Endpoint para manejar la cancelación del pago
    @GetMapping("/cancel")
    public ResponseEntity<String> cancel() {
        return ResponseEntity.ok("El pago fue cancelado");
    }
}
