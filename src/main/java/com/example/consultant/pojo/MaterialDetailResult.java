package com.example.consultant.pojo;

import lombok.Data;

import java.util.List;

@Data
public class MaterialDetailResult {
    private MaterialInfo material;
    private List<MaterialPrice> prices;
    private List<MaterialStockResult> stocks;
}
