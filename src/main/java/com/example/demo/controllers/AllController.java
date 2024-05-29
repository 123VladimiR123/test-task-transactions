package com.example.demo.controllers;

import com.example.demo.dto.RandomEntityDTO;
import com.example.demo.entities.RandomEntity;
import com.example.demo.repositories.AllRepository;
import jakarta.el.PropertyNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/record")
@AllArgsConstructor
public class AllController {
    private final AllRepository allRepository;

    // Получение одного элемента по id
    @GetMapping("/{id}")
    public ResponseEntity<Mono<RandomEntity>> getOne(@PathVariable(name = "id") Long id)
            throws NumberFormatException, ExecutionException, InterruptedException, TimeoutException {
        Mono<RandomEntity> entityMono = allRepository.findById(id);

        // Timeout на случай проблем с дб
        // В случае несуществующего id - вернем 404
        if (entityMono.toFuture().get(1000, TimeUnit.MILLISECONDS) == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(entityMono);
    }


    @GetMapping
    public ResponseEntity<Flux<RandomEntity>> getMany(@RequestParam(name = "p", required = false, defaultValue = "1") Integer page,
                                                      @RequestParam(name = "c", required = false, defaultValue = "100") Integer count)
            throws NumberFormatException {
        if (page <= 0 || count <= 10) return ResponseEntity.status(400).build();

        // Мультивыборка с пагинацией в основном для удобного теста,
        // а не миллионого findBy
        return ResponseEntity.ok(allRepository.findAllBy(PageRequest.of(page - 1, count)));
    }


    @PostMapping
    public ResponseEntity<Flux<RandomEntity>> createOneOrMore(@RequestBody List<@Valid RandomEntityDTO> dtos)
            throws ExecutionException, InterruptedException, TimeoutException {

        // Просто валидирую по соответствию uuid, сохраняем все
        // В зависимости от контекста можно проверить каждый на уже существующую запись в дб,
        // тогда получим чистый write
        Flux<RandomEntity> savedEntities = allRepository.saveAll(
                dtos
                        .stream()
                        .map(e -> new RandomEntity(e.getUuid()))
                        .collect(Collectors.toList()));

        // Даю timeout с запасом
        // Если, всё же, timeout вышел, частично оставляем в бд, ошибка timedout перехватится далее
        if (savedEntities.count().toFuture().get(dtos.size() * 100L, TimeUnit.MILLISECONDS) != dtos.size())
            return ResponseEntity.status(206).body(savedEntities);
        return ResponseEntity.ok(savedEntities);
    }


    // С учётом использования REST, пользуюсь HTTP методами по полной
    @PatchMapping
    public ResponseEntity<Mono<RandomEntity>> updateOne(@RequestBody @Valid RandomEntityDTO dto,
                                                        @RequestParam(name = "id") @NotEmpty Long id)
            throws NumberFormatException, PropertyNotFoundException, ExecutionException, InterruptedException, TimeoutException {

        // Перед обновлением проверим, существует ли такой entity
        RandomEntity oldEntity = allRepository
                .findById(id)
                .toFuture()
                .get(1000, TimeUnit.MILLISECONDS);

        // 404 если не найдено
        if (oldEntity == null) throw new PropertyNotFoundException();

        Mono<RandomEntity> saved = allRepository.save(new RandomEntity(id, dto.getUuid()));

        return ResponseEntity.ok(saved);
    }


    @DeleteMapping
    public ResponseEntity<Mono<Void>> deleteOne(@RequestParam(name = "id") @NotNull Long id)
            throws NumberFormatException, PropertyNotFoundException, ExecutionException, InterruptedException, TimeoutException {

        // Перед удалением проверяем на существование, иначе - 404
        if (!allRepository.existsById(id).toFuture().get(1000, TimeUnit.MILLISECONDS)) throw new PropertyNotFoundException();
        return ResponseEntity.ok(allRepository.deleteById(id));
    }


    // Сущность не найдена
    @ExceptionHandler({PropertyNotFoundException.class})
    public ResponseEntity notFoundHandler() {
        return ResponseEntity.notFound().build();
    }

    // Некорректные значения чисел (id, страница, кол-во элементов на странице)
    @ExceptionHandler({NumberFormatException.class})
    public ResponseEntity unparsedNumber() {
        return ResponseEntity.badRequest().build();
    }

    // На случай timeout-ов
    @ExceptionHandler({ExecutionException.class, InterruptedException.class, TimeoutException.class})
    public ResponseEntity partiallySaved() {
        return ResponseEntity.status(206).build();
    }

    // Если UUID некорректен
    @ExceptionHandler({ValidationException.class})
    public ResponseEntity brokenDto() {
        return ResponseEntity.badRequest().build();
    }

}
