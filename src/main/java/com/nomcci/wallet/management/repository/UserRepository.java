package com.nomcci.wallet.management.repository;

import com.nomcci.wallet.management.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
}
