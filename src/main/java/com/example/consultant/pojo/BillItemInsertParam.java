package com.example.consultant.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillItemInsertParam {
    private Long id;
    private Long headerId;
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
    private LocalDateTime expirationDate;
    private Long linkId;
    private Long tenantId;
}
