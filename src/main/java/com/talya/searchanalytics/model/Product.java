package com.talya.searchanalytics.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Product {
    private String productId;
    private String name;
    private Double price;
    private Integer amount;
}