package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillItemResult {
    private Long id;
    private Long materialId;
    private String materialName;
    private Long materialExtendId;
    private String materialUnit;
    private BigDecimal operNumber;
    private BigDecimal basicNumber;
    private BigDecimal unitPrice;
    private BigDecimal purchaseUnitPrice;
    private BigDecimal allPrice;
    private Long depotId;
    private String depotName;
    private Long anotherDepotId;
    private BigDecimal taxRate;
    private BigDecimal taxMoney;
    private BigDecimal taxLastMoney;
    private String batchNumber;
    private LocalDateTime expirationDate;
    private String remark;
}
