package com.example.demo.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Table("ids")
public class RandomEntity {

    @Id
    @Column(value = "id")
    private Long id;

    // Добавил, чтобы не держать одно лишь поле id и хоть что-то обновлять
    // Смысла в десятках полей не увидел
    @Column(value = "uuid")
    private UUID uuid;

    public RandomEntity(UUID uuid) {
        this.uuid = uuid;
    }
}
