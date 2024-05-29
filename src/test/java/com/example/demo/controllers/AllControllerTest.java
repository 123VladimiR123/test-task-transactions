package com.example.demo.controllers;

import com.example.demo.dto.RandomEntityDTO;
import com.example.demo.entities.RandomEntity;
import com.example.demo.repositories.AllRepository;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class AllControllerTest {
    @Autowired
    public AllRepository repository;
    @Autowired
    public AllController controller;

    @SneakyThrows
    @Test
    void getOne() {
        RandomEntity expected = repository.save(new RandomEntity(UUID.randomUUID())).toFuture().get();
        RandomEntity actual = controller.getOne(expected.getId()).getBody().toFuture().get();
        assertEquals(expected, actual, "Entities are not equal");
    }

    @SneakyThrows
    @Test
    void getMany() {
        // Для начала вбросим в дб миллион случайных записей
        List<RandomEntity> entities = new ArrayList<>(1_000_000);
        for (int i = 0; i < 1_000_000; i++)
            entities.add(new RandomEntity(UUID.randomUUID()));

        // Проверяем, всё ли сохранилось
        assertEquals(repository.saveAll(entities).count().toFuture().get(), Long.valueOf(1_000_000), "something went wrong");

        // В данном случае - сотня параллельных транзакций по 100к
        final int totalRuns = 100;

        // Для имитации параллельности запустим сотню одновременно
        ExecutorService executorService = Executors.newFixedThreadPool(totalRuns);
        // Куда потоки записывают своё время на чтение
        ConcurrentHashMap<String, Long> times = new ConcurrentHashMap<>();

        // Общее время параллельных чтений
        Instant begin = Instant.now();

        for (int i = 1; i <= totalRuns; i++)
            executorService.execute(new timePerOne(controller, times));

        // Ждём завершения сотни
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);

        Instant end = Instant.now();

        // Логируем полученные результаты, для простоты в log файл
        log.info(" ");
        log.info("Завершилась работа " + times.size() + " потоков чтения 10 000 записей");
        log.info("Общее время параллельного выполнения " + Duration.between(begin, end).toMillis() + " мс");

        List<Long> millis = times.values().stream().collect(Collectors.toList());
        log.info("Суммарное время выполнения " + millis.stream().mapToLong(Long::longValue).sum() + " мс");

        millis.sort(Comparator.naturalOrder());

        long medianTime = (millis.size() % 2 == 0) ?
                (millis.get(millis.size() / 2) + millis.get(millis.size() / 2 - 1)) / 2 :
                millis.get(millis.size()) / 2;

        log.info("Медианное время " + medianTime + " мс");
        assertEquals(times.size(), totalRuns);

        log.info("95 процентиь " + millis.stream().skip((long) (totalRuns * 0.95 - 2)).findFirst().get() + " мс");
        log.info("99 процентиь " + millis.stream().skip((long) (totalRuns * 0.99 - 2)).findFirst().get() + " мс");
        log.info(" ");
    }

    // Имитация параллельности транзакций
    @AllArgsConstructor
    private class timePerOne implements Runnable {
        private final AllController controller;
        private final ConcurrentHashMap<String, Long> times;

        @SneakyThrows
        @Override
        public void run() {
            // Т.к. решил использовать пагинацию, выбираем страницу для учёта задержек offset'а
            Random random = new Random();

            // Фиксируем время только чтения, count просто для блокирующего ожидания завершения
            Instant begin = Instant.now();
            controller.getMany(random.nextInt(1, 10), 10_000).getBody().count().toFuture().get();
            Instant end = Instant.now();

            // Добавляем время для текущего потока
            times.put(Thread.currentThread().getName(), Duration.between(begin, end).toMillis());
        }
    }

    // Тест на создание 100к записей
    // Сбор статистики не требовался, но это явно дольше, чем чтение
    @SneakyThrows
    @Test
    void createOneOrMore() {
        List<RandomEntityDTO> entities = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++)
            entities.add(new RandomEntityDTO(UUID.randomUUID()));

        assertEquals(controller.createOneOrMore(entities).getBody().count().toFuture().get(), Long.valueOf(100_000), "wrote wrong number");
    }

    @SneakyThrows
    @Test
    void updateOne() {
        RandomEntity expected = repository.save(new RandomEntity(UUID.randomUUID())).toFuture().get();
        RandomEntityDTO dto = new RandomEntityDTO(UUID.randomUUID());

        RandomEntity actual = controller.updateOne(dto, expected.getId()).getBody().toFuture().get();

        expected.setUuid(dto.getUuid());

        assertEquals(expected, actual, "Updated wrong entity");
    }

    @SneakyThrows
    @Test
    void deleteOne() {
        RandomEntity entity = repository.save(new RandomEntity(UUID.randomUUID())).toFuture().get();

        controller.deleteOne(entity.getId()).getBody().toFuture().get();

        assertNull(repository.findById(entity.getId()).toFuture().get(), "found entity after delete");
    }
}