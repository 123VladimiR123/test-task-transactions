package com.example.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class RandomEntityDTO {

    // В лучших традициях, всё получаемое прогоняем через DTO
    @NotEmpty
    @NotNull
    @org.hibernate.validator.constraints.UUID
    private final UUID uuid;
}
