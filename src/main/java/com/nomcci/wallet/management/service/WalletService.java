package com.nomcci.wallet.management.service;

import com.nomcci.wallet.management.dto.TransactionDTO;
import com.nomcci.wallet.management.model.*;
import com.nomcci.wallet.management.repository.*;
import com.nomcci.wallet.management.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionSummaryRepository transactionSummaryRepository;
    private final ArchivedTransactionRepository archivedTransactionRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url}")
    private String authUrl;


    /**
     * Deposita la cantidad especificada en la billetera
     * @param walletId id de la billetera
     * @param amount cantidad a depositar
     * @return billetera con saldo recalculado
     */
    @Transactional
    public Wallet deposit(Long walletId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));

        // Crea y guarda la transaccion
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setTimestamp(Instant.now());
        transactionRepository.save(transaction);

        // Calcula el saldo
        return recalculateBalance(walletId);
    }

    @Transactional
    public Wallet deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(jwt.getSubject());

        System.out.println("Id: "+userId);

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for the user."));

        // Crea y guarda la transaccion
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setTimestamp(Instant.now());
        transactionRepository.save(transaction);

        // Calcula el saldo
        return recalculateBalance(wallet.getId());
    }

    /**
     * Retira la cantidad seleccionada de la billetera asociada al usuario autenticado.
     *
     * @param amount cantidad a retirar
     * @return billetera con saldo recalculado
     */
    @Transactional
    public Wallet withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }

        // Obtiene el usuario autenticado desde el JWT
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(jwt.getClaim("sub").toString());


        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada para el usuario"));

        // Revisa si la billetera tiene suficiente saldo
        BigDecimal currentBalance = wallet.getBalance();
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }

        // Crea y guarda la transacción
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount.negate());
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setTimestamp(Instant.now());
        transactionRepository.save(transaction);

        // Recalcula el saldo de la billetera
        return recalculateBalance(wallet.getId());
    }

    /**
     * Transfiere la cantidad especificada de una billetera a otra.
     *
     * @param toEmail Correo electrónico de la billetera de destino.
     * @param amount  Cantidad de saldo a transferir.
     */
    @Transactional
    public void transfer(String toEmail, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Extrae el userId del JWT
        Long userId = Long.parseLong(jwt.getSubject());

        // Busca la billetera asociada al usuario
        Wallet fromWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for the user."));

        // Busca la billetera asociada al usuario destino
        Long toUserId = getUserIdByEmail(toEmail);
        Wallet toWallet = walletRepository.findByUserId(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("Destination wallet not found."));

        if (fromWallet.getId().equals(toWallet.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet.");
        }

        // Revisa si la billetera que transfiere tiene suficiente saldo
        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in source wallet.");
        }

        // Crea y guarda una nueva transacción de transferencia
        Transaction transferTransaction = new Transaction();

        // Transacción de salida (de la billetera del usuario)
        transferTransaction.setWallet(fromWallet);
        transferTransaction.setDestinationWallet(toWallet);
        transferTransaction.setAmount(amount.negate()); // Restamos el monto de la cuenta de origen
        transferTransaction.setTransactionType(TransactionType.TRANSFER);
        transferTransaction.setTimestamp(Instant.now());
        transactionRepository.save(transferTransaction);

        // Transacción de entrada (de la billetera del destinatario)
        Transaction receiveTransaction = new Transaction();
        receiveTransaction.setWallet(toWallet);
        receiveTransaction.setDestinationWallet(fromWallet);
        receiveTransaction.setAmount(amount); // Añadimos el monto a la cuenta de destino
        receiveTransaction.setTransactionType(TransactionType.TRANSFER);
        receiveTransaction.setTimestamp(Instant.now());
        transactionRepository.save(receiveTransaction);

        // Actualizamos los saldos de las billeteras
        fromWallet.setBalance(fromWallet.getBalance().subtract(amount));
        toWallet.setBalance(toWallet.getBalance().add(amount));

        // Guardamos los cambios en las billeteras
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
    }


    @Transactional
    public BigDecimal getBalance() {
        Logger logger = LoggerFactory.getLogger(WalletService.class);

        try {
            // Obtén el token JWT del contexto de seguridad
            Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            // Extrae el userId del JWT
            Long userId = Long.parseLong(jwt.getSubject());
            logger.info("Usuario autenticado (ID): {}", userId);

            // Busca la billetera asociada al usuario
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found for the user."));

            // Calcula y devuelve el balance
            return recalculateBalance(wallet.getId()).getBalance();
        } catch (Exception e) {
            logger.error("Error al obtener el balance de la billetera", e);
            throw e;
        }
    }


    /**
     * Obtiene el historial de transacciones de una billetera con soporte para paginación y búsqueda por rango de fechas.
     *
     * @param page Número de página (comienza en 0).
     * @param size Tamaño de la página.
     * @param sortBy Campo por el cual ordenar (opcional, por defecto "timestamp").
     * @param startTimestamp Inicio del rango de fechas (opcional).
     * @param endTimestamp Fin del rango de fechas (opcional).
     * @return Página de transacciones.
     */
    public Page<TransactionDTO> getTransactionHistory(
            int page,
            int size,
            String sortBy,
            Instant startTimestamp,
            Instant endTimestamp
    ) {
        // Obtiene el usuario autenticado desde el JWT
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(jwt.getClaim("sub").toString());

        // Obtiene la billetera asociada al usuario
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada para el usuario"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        // Configura el rango de fechas si es null
        if (startTimestamp == null) {
            startTimestamp = Instant.EPOCH;
        }
        if (endTimestamp == null) {
            endTimestamp = Instant.now();
        }

        // Busca las transacciones activas
        Page<Transaction> activeTransactions = transactionRepository.findByWalletAndTimestampBetween(wallet, startTimestamp, endTimestamp, pageable);

        // Busca las transacciones archivadas
        Pageable archivedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
        Page<ArchivedTransaction> archivedTransactions = archivedTransactionRepository.findByWalletAndTimestampBetween(wallet, startTimestamp, endTimestamp, archivedPageable);

        // Mapea transacciones activas a DTO
        List<TransactionDTO> allTransactionDTOs = new ArrayList<>(activeTransactions.stream()
                .map(tx -> {
                    String firstName = "N/A"; // Valor predeterminado

                    if (tx.getDestinationWallet() != null) {
                        Long toUserId = tx.getDestinationWallet().getUserId(); // Obtener el userId
                        firstName = getFirstNameById(toUserId); // Llamada al servicio para obtener el nombre
                    }

                    return new TransactionDTO(
                            tx.getTimestamp(),
                            firstName,
                            tx.getTransactionType().name(),
                            tx.getAmount()
                    );
                })
                .toList());

        // Mapea transacciones archivadas a DTO
        for (ArchivedTransaction archivedTransaction : archivedTransactions) {
            String firstName = "N/A"; // Valor predeterminado

            if (archivedTransaction.getDestinationWallet() != null && archivedTransaction.getDestinationWallet().getUserId() != null) {
                Long toUserId = archivedTransaction.getDestinationWallet().getUserId();
                firstName = getFirstNameById(toUserId);
            }

            TransactionDTO transactionDTO = new TransactionDTO(
                    archivedTransaction.getTimestamp(),
                    firstName,
                    archivedTransaction.getTransactionType().name(),
                    archivedTransaction.getAmount()
            );
            allTransactionDTOs.add(transactionDTO);
        }


        return new PageImpl<>(allTransactionDTOs, pageable, activeTransactions.getTotalElements() + archivedTransactions.getTotalElements());
    }



    /**
     * Calcula el saldo de la billetera segun el historial de transacciones
     * Esto asegura la consistencia en el saldo de la billetera.
     * @param walletId id de la billetera a recalcular
     * @return billetera con saldo recalculado
     */
    @Transactional
    public Wallet recalculateBalance(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));

        Instant cutoffDate = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Transaction> transactionsToArchive = transactionRepository.findOldTransactions(wallet, cutoffDate);

        if (!transactionsToArchive.isEmpty()) {
            archiveTransactions(wallet, transactionsToArchive);
        }

        BigDecimal summarizedBalance = transactionSummaryRepository.findByWallet(wallet)
                .stream()
                .map(TransactionSummary::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recentTransactionsBalance = transactionRepository.findRecentTransactions(wallet, cutoffDate)
                .stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recalculatedBalance = summarizedBalance.add(recentTransactionsBalance);

        wallet.setBalance(recalculatedBalance);
        return walletRepository.save(wallet);
    }


    @Transactional
    public void archiveTransactions(Wallet wallet, List<Transaction> transactionsToArchive) {
        // Agrupa las transacciones por período (ejemplo: mensual)
        Map<String, List<Transaction>> groupedTransactions = transactionsToArchive.stream()
                .collect(Collectors.groupingBy(transaction -> {
                    Instant timestamp = transaction.getTimestamp();
                    return timestamp.toString().substring(0, 7); 
                }));

        // Crea resúmenes de las transacciones
        List<TransactionSummary> summaries = groupedTransactions.values().stream()
                .map(grouped -> {

                    TransactionSummary summary = new TransactionSummary();
                    summary.setWallet(wallet);
                    summary.setStartDate(grouped.get(0).getTimestamp());
                    summary.setEndDate(grouped.get(grouped.size() - 1).getTimestamp());
                    summary.setTotalAmount(grouped.stream()
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));

                    return summary;
                })
                .toList();

        // Guarda los resúmenes en la tabla de resúmenes
        transactionSummaryRepository.saveAll(summaries);

        // Mueve transacciones a la tabla de archivo
        List<ArchivedTransaction> archivedTransactions = transactionsToArchive.stream()
                .map(transaction -> {
                    ArchivedTransaction archivedTransaction = new ArchivedTransaction();
                    archivedTransaction.setWallet(transaction.getWallet());
                    archivedTransaction.setAmount(transaction.getAmount());
                    archivedTransaction.setTransactionType(transaction.getTransactionType());
                    archivedTransaction.setTimestamp(transaction.getTimestamp());
                    archivedTransaction.setArchivedAt(Instant.now());
                    archivedTransaction.setDestinationWallet(transaction.getDestinationWallet());
                    return archivedTransaction;
                })
                .toList();

        archivedTransactionRepository.saveAll(archivedTransactions);

        // Elimina las transacciones antiguas de la tabla principal
        transactionRepository.deleteAll(transactionsToArchive);
    }

    public Wallet createWallet() {
        Logger logger = LoggerFactory.getLogger(WalletService.class); 

        try {
            // Obtén el token JWT del contexto de seguridad
            Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            Long userId = Long.parseLong(jwt.getClaim("sub").toString());

            // Verifica si el usuario ya tiene una wallet asociada
            if (walletRepository.existsByUserId(userId)) {
                throw new IllegalArgumentException("El usuario ya tiene una wallet asociada");
            }

            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setCurrency("USD");
            wallet.setActive(true);

            walletRepository.save(wallet);

            return wallet;

        } catch (Exception e) {
            logger.error("Error procesando el token JWT", e);
            throw e;
        }
    }

    /**
     * Transfiere la cantidad especificada de una billetera a otra
     * @param toWalletId id de la billetera a la que sera transferido el saldo
     * @param amount cantidad de saldo a transferir
     */
    @Transactional
    public void transfer(Long fromWalletId, Long toWalletId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }

        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet.");
        }

        Wallet fromWallet = walletRepository.findById(fromWalletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
        Wallet toWallet = walletRepository.findById(toWalletId)
                .orElseThrow(() -> new IllegalArgumentException("Destination wallet not found."));

        // Revisa si la billetera que transfiere tiene suficiente saldo
        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in source wallet.");
        }

        // Crea y guarda una nueva transacción de transferencia
        Transaction transferTransaction = new Transaction();

        // Transacción de salida (de la billetera del usuario)
        transferTransaction.setWallet(fromWallet);
        transferTransaction.setDestinationWallet(toWallet);
        transferTransaction.setAmount(amount.negate()); // Restamos el monto de la cuenta de origen
        transferTransaction.setTransactionType(TransactionType.TRANSFER);
        transferTransaction.setTimestamp(Instant.now());
        transactionRepository.save(transferTransaction);

        // Transacción de entrada (de la billetera del destinatario)
        Transaction receiveTransaction = new Transaction();
        receiveTransaction.setWallet(toWallet);
        receiveTransaction.setDestinationWallet(fromWallet);
        receiveTransaction.setAmount(amount); // Añadimos el monto a la cuenta de destino
        receiveTransaction.setTransactionType(TransactionType.TRANSFER);
        receiveTransaction.setTimestamp(Instant.now());
        transactionRepository.save(receiveTransaction);

        // Actualizamos los saldos de las billeteras
        fromWallet.setBalance(fromWallet.getBalance().subtract(amount));
        toWallet.setBalance(toWallet.getBalance().add(amount));

        // Guardamos los cambios en las billeteras
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
    }

    /**
     * Retira la cantidad seleccionada de la billetera asociada al usuario autenticado.
     *
     * @param amount cantidad a retirar
     * @return billetera con saldo recalculado
     */
    @Transactional
    public Wallet withdraw(Long walletId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }


        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada para el usuario"));

        // Revisa si la billetera tiene suficiente saldo
        BigDecimal currentBalance = wallet.getBalance();
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }

        // Crea y guarda la transacción
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount.negate());
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setTimestamp(Instant.now());
        transactionRepository.save(transaction);

        // Recalcula el saldo de la billetera
        return recalculateBalance(wallet.getId());
    }

    /**
     * Obtiene el historial de transacciones de una billetera con soporte para paginación y búsqueda por rango de fechas.
     *
     * @param page Número de página (comienza en 0).
     * @param size Tamaño de la página.
     * @param sortBy Campo por el cual ordenar (opcional, por defecto "timestamp").
     * @param startTimestamp Inicio del rango de fechas (opcional).
     * @param endTimestamp Fin del rango de fechas (opcional).
     * @return Página de transacciones.
     */
    public PageImpl<TransactionDTO> getTransactionHistory(
            Long walletId,
            int page,
            int size,
            String sortBy,
            Instant startTimestamp,
            Instant endTimestamp
    ) {

        // Obtiene la billetera asociada al usuario
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Billetera no encontrada para el usuario"));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        // Configura el rango de fechas si es null
        if (startTimestamp == null) {
            startTimestamp = Instant.EPOCH;
        }
        if (endTimestamp == null) {
            endTimestamp = Instant.now();
        }

        // Busca las transacciones activas
        Page<Transaction> activeTransactions = transactionRepository.findByWalletAndTimestampBetween(wallet, startTimestamp, endTimestamp, pageable);

        // Busca las transacciones archivadas
        Pageable archivedPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
        Page<ArchivedTransaction> archivedTransactions = archivedTransactionRepository.findByWalletAndTimestampBetween(wallet, startTimestamp, endTimestamp, archivedPageable);

        // Mapea transacciones activas a DTO
        List<TransactionDTO> allTransactionDTOs = new ArrayList<>(activeTransactions.stream()
                .map(tx -> {
                    String firstName = "N/A"; // Valor predeterminado

                    if (tx.getDestinationWallet() != null) {
                        Long userId = tx.getDestinationWallet().getUserId(); // Obtener el userId
                        firstName = getFirstNameById(userId); // Llamada al servicio para obtener el nombre
                    }

                    return new TransactionDTO(
                            tx.getTimestamp(),
                            firstName,
                            tx.getTransactionType().name(),
                            tx.getAmount()
                    );
                })
                .toList());

        // Mapea transacciones archivadas a DTO
        for (ArchivedTransaction archivedTransaction : archivedTransactions) {
            String firstName = "N/A"; // Valor predeterminado

            if (archivedTransaction.getDestinationWallet() != null && archivedTransaction.getDestinationWallet().getUserId() != null) {
                Long userId = archivedTransaction.getDestinationWallet().getUserId();
                firstName = getFirstNameById(userId);
            }

            TransactionDTO transactionDTO = new TransactionDTO(
                    archivedTransaction.getTimestamp(),
                    firstName,
                    archivedTransaction.getTransactionType().name(),
                    archivedTransaction.getAmount()
            );
            allTransactionDTOs.add(transactionDTO);
        }


        return new PageImpl<>(allTransactionDTOs, pageable, activeTransactions.getTotalElements() + archivedTransactions.getTotalElements());
    }

    public String getFirstNameById(Long userId) {
        String url = authUrl + "/internal/wallet/get-user-by-id/" + userId;

        // Configurar los headers con el token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtUtil.generateServiceToken());

        // Crear la entidad HTTP
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Hacer la solicitud GET
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        return response.getBody();
    }

    public Long getUserIdByEmail(String email) {
        try{
            String url = authUrl + "/internal/wallet/get-id-by-email?email=" + email;

            // Configurar los headers con el token
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtUtil.generateServiceToken());
            // Imprimir los headers para ver si el token está presente
            System.out.println("Authorization Header: " + headers.getFirst(HttpHeaders.AUTHORIZATION));


            // Crear la entidad HTTP
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Hacer la solicitud GET
            ResponseEntity<Long> response = restTemplate.exchange(url, HttpMethod.GET, entity, Long.class);

            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            System.out.println("Error al hacer la solicitud: " + ex.getMessage());
            System.out.println("Detalles del error: " + ex.getResponseBodyAsString());
            throw new RuntimeException("Error al hacer la solicitud", ex);
        } catch (Exception e) {
            System.out.println("Error general: " + e.getMessage());
            throw new RuntimeException("Error al hacer la solicitud", e);
        }
    }
}
