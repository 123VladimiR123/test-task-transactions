package com.example.demo.repositories;

import com.example.demo.entities.RandomEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

// Поднял уровень изоляции на случай тестов параллельного удаления/обновления с чтением
// Сначала подумал, что будут вложенные транзакции
// Захотел параллельность на тесте, поэтому реакт
@Transactional(isolation = Isolation.REPEATABLE_READ,
        propagation = Propagation.REQUIRES_NEW)
@Repository
public interface AllRepository extends ReactiveCrudRepository<RandomEntity, Long> {
    Flux<RandomEntity> findAllBy(Pageable pageable);
}
