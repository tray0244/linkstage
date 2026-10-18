package com.linkstage.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkstage.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	// hold_group_id는 UNIQUE라 많아야 하나다.
	Optional<Payment> findByHoldGroupId(Long holdGroupId);

}
