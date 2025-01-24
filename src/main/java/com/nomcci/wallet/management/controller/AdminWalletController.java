package com.nomcci.wallet.management.controller;

import com.nomcci.wallet.management.model.Transaction;
import com.nomcci.wallet.management.model.Wallet;
import com.nomcci.wallet.management.service.WalletService;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/wallet/admin")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;

    /**
     * Deposita dinero en la billetera.
     *
     * @param walletId ID de la billetera.
     * @param amount   Cantidad a depositar.
     * @return Billetera con el saldo actualizado.
     */
    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<Wallet> deposit(
            @PathVariable Long walletId,
            @RequestParam BigDecimal amount) {
        Wallet updatedWallet = walletService.deposit(walletId, amount);
        return ResponseEntity.ok(updatedWallet);
    }

    /**
     * Retira dinero de la billetera.
     *
     * @param amount   Cantidad a retirar.
     * @return Billetera con el saldo actualizado.
     */
    @PostMapping("/{walletId}/withdraw")
    public ResponseEntity<Wallet> withdraw(
            @PathVariable Long walletId,
            @RequestParam BigDecimal amount) {
        Wallet updatedWallet = walletService.withdraw(walletId, amount);
        return ResponseEntity.ok(updatedWallet);
    }

    /**
     * Transfiere dinero de una billetera a otra.
     *
     * @param fromWalletId ID de la billetera de origen.
     * @param toWalletId   ID de la billetera de destino.
     * @param amount       Cantidad a transferir.
     * @return Respuesta exitosa si la transferencia se realizó correctamente.
     */
    @PostMapping("/{fromWalletId}/transfer")
    public ResponseEntity<Void> transfer(
            @PathVariable Long fromWalletId,
            @RequestParam Long toWalletId,
            @RequestParam BigDecimal amount) {
        walletService.transfer(fromWalletId, toWalletId, amount);
        return ResponseEntity.ok().build();
    }

    /**
     * Obtiene el historial de transacciones de una billetera.
     *
     * @param page           Número de página.
     * @param size           Tamaño de la página.
     * @param sortBy         Campo por el cual ordenar.
     * @param startTimestamp Inicio del rango de fechas.
     * @param endTimestamp   Fin del rango de fechas.
     * @return Historial paginado de transacciones.
     */
    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<Page<Transaction>> getTransactionHistory(
            @PathVariable Long walletId,
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(required = false) Instant startTimestamp,
            @RequestParam(required = false) Instant endTimestamp ) {
        Page<Transaction> transactions = walletService.getTransactionHistory(walletId, page, size, sortBy, startTimestamp, endTimestamp);
        return ResponseEntity.ok(transactions);
    }
}
