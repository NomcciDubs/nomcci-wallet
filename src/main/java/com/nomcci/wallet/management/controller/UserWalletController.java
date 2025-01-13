package com.nomcci.wallet.management.controller;

import com.nomcci.wallet.management.dto.ErrorResponse;
import com.nomcci.wallet.management.exception.InsufficientFundsException;
import com.nomcci.wallet.management.exception.WalletNotFoundException;
import com.nomcci.wallet.management.model.Wallet;
import com.nomcci.wallet.management.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet/user")
@RequiredArgsConstructor
public class UserWalletController {

    private final WalletService walletService;

    /**
     * Permite retirar dinero de la billetera del usuario.
     *
     * @param amount   Cantidad a retirar.
     * @return Billetera con el saldo actualizado.
     */
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestParam BigDecimal amount) {
        try {
            Wallet updatedWallet = walletService.withdraw(amount);
            return ResponseEntity.ok(updatedWallet);
        } catch (WalletNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Wallet not found", e.getMessage()));
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Insufficient funds", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    /**
     * Permite transferir dinero de una billetera del usuario a otra.
     *
     * @param toWalletId   ID de la billetera de destino.
     * @param amount       Cantidad a transferir.
     * @return Respuesta exitosa si la transferencia se realizó correctamente.
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @RequestParam Long toWalletId,
            @RequestParam BigDecimal amount) {
        try {
            walletService.transfer(toWalletId, amount);
            return ResponseEntity.ok().build();
        } catch (WalletNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Wallet not found", e.getMessage()));
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Insufficient funds", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getBalance() {
        try {
            BigDecimal balance = walletService.getBalance();
            return ResponseEntity.ok(balance);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Wallet not found for the user", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal server error", e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createWallet(@RequestHeader("Authorization") String token) {

        Wallet wallet = walletService.createWallet();
        return new ResponseEntity<>(wallet, HttpStatus.CREATED);

    }

    // Manejadores de excepciones globales (opcional)
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFoundException(WalletNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Wallet not found", e.getMessage()));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(InsufficientFundsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Insufficient funds", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Internal server error", e.getMessage()));
    }


}
