package com.example.consultant.pojo;

import lombok.Data;

@Data
public class ErpSystemConfig {
    private String companyName;
    private String companyContacts;
    private String companyAddress;
    private String companyTel;
    private String companyFax;
    private String companyPostCode;
    private String saleAgreement;
    private String depotFlag;
    private String customerFlag;
    private String minusStockFlag;
    private String purchaseBySaleFlag;
    private String multiLevelApprovalFlag;
    private String forceApprovalFlag;
    private String updateUnitPriceFlag;
    private String overLinkBillFlag;
    private String inOutManageFlag;
    private String multiAccountFlag;
    private String moveAvgPriceFlag;
    private String auditPrintFlag;
    private String zeroChangeAmountFlag;
    private String customerStaticPriceFlag;
}
