package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillItemRequest {
    private Long materialId;
    private Long materialExtendId;
    private String materialUnit;
    private String sku;
    private BigDecimal operNumber;
    private BigDecimal basicNumber;
    private BigDecimal unitPrice;
    private BigDecimal purchaseUnitPrice;
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
