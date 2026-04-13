package com.example.consultant.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillItemRequest {
    private Long materialId;
    private Long materialExtendId;
    @JsonAlias({"unit", "commodityUnit"})
    private String materialUnit;
    private String sku;
    @JsonAlias({"number", "quantity", "count"})
    private BigDecimal operNumber;
    private BigDecimal basicNumber;
    @JsonAlias({"price", "purchasePrice", "salePrice"})
    private BigDecimal unitPrice;
    private BigDecimal purchaseUnitPrice;
    @JsonAlias({"totalPrice", "amount"})
    private BigDecimal allPrice;
    private String remark;
    private Long depotId;
    private Long anotherDepotId;
    private BigDecimal taxRate;
    private BigDecimal taxMoney;
    private BigDecimal taxLastMoney;
    private String materialType;
    private String snList;
    private String batchNumber;
    private String expirationDate;
    private Long linkId;
}
