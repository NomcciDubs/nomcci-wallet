package com.nomcci.wallet.management.service;

import com.nomcci.wallet.management.model.*;
import com.nomcci.wallet.management.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import org.springframework.stereotype.Service;

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
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionSummaryRepository transactionSummaryRepository;
    private final ArchivedTransactionRepository archivedTransactionRepository;

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
     * Transfiere la cantidad especificada de una billetera a otra
     * @param toWalletId id de la billetera a la que sera transferido el saldo
     * @param amount cantidad de saldo a transferir
     */
    @Transactional
    public void transfer(Long toWalletId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero.");
        }

        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Extrae el userId del JWT
        Long userId = Long.parseLong(jwt.getSubject());

        // Busca la billetera asociada al usuario
        Wallet fromWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for the user."));

        if (fromWallet.getId().equals(toWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet.");
        }

        Wallet toWallet = walletRepository.findById(toWalletId)
                .orElseThrow(() -> new IllegalArgumentException("Destination wallet not found."));

        // Revisa si la billetera que transfiere tiene suficiente saldo
        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in source wallet.");
        }

        // Crea y guarda una nueva transaccion de Transfer
        Transaction transferTransaction = new Transaction();
        transferTransaction.setWallet(fromWallet);
        transferTransaction.setDestinationWallet(toWallet);
        transferTransaction.setAmount(amount);
        transferTransaction.setTransactionType(TransactionType.TRANSFER);
        transferTransaction.setTimestamp(Instant.now());
        transactionRepository.save(transferTransaction);

        // Recalcula el balance de ambas billeteras
        recalculateBalance(fromWallet.getId());
        recalculateBalance(toWalletId);
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
    public Page<Transaction> getTransactionHistory(
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

        // Une las transacciones activas y archivadas
        List<Transaction> allTransactions = new ArrayList<>(activeTransactions.getContent());

        for (ArchivedTransaction archivedTransaction : archivedTransactions) {
            Transaction transaction = new Transaction();
            transaction.setId(archivedTransaction.getId());
            transaction.setWallet(archivedTransaction.getWallet());
            transaction.setAmount(archivedTransaction.getAmount());
            transaction.setTransactionType(archivedTransaction.getTransactionType());
            transaction.setTimestamp(archivedTransaction.getTimestamp());
            allTransactions.add(transaction);
        }

        return new PageImpl<>(allTransactions, pageable, activeTransactions.getTotalElements() + archivedTransactions.getTotalElements());
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

            // Busca el usuario en la base de datos
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            // Verifica si el usuario ya tiene una wallet asociada
            if (walletRepository.existsByUser(user)) {
                throw new IllegalArgumentException("El usuario ya tiene una wallet asociada");
            }

            Wallet wallet = new Wallet();
            wallet.setUser(user);
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

        // Crea y guarda una nueva transaccion de Transfer
        Transaction transferTransaction = new Transaction();
        transferTransaction.setWallet(fromWallet);
        transferTransaction.setDestinationWallet(toWallet);
        transferTransaction.setAmount(amount);
        transferTransaction.setTransactionType(TransactionType.TRANSFER);
        transferTransaction.setTimestamp(Instant.now());
        transactionRepository.save(transferTransaction);

        // Recalcula el balance de ambas billeteras
        recalculateBalance(fromWallet.getId());
        recalculateBalance(toWalletId);
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
    public Page<Transaction> getTransactionHistory(
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

        // Une las transacciones activas y archivadas
        List<Transaction> allTransactions = new ArrayList<>(activeTransactions.getContent());

        for (ArchivedTransaction archivedTransaction : archivedTransactions) {
            Transaction transaction = new Transaction();
            transaction.setId(archivedTransaction.getId());
            transaction.setWallet(archivedTransaction.getWallet());
            transaction.setAmount(archivedTransaction.getAmount());
            transaction.setTransactionType(archivedTransaction.getTransactionType());
            transaction.setTimestamp(archivedTransaction.getTimestamp());
            allTransactions.add(transaction);
        }

        return new PageImpl<>(allTransactions, pageable, activeTransactions.getTotalElements() + archivedTransactions.getTotalElements());
    }
}
