package com.example.consultant.service;

import com.example.consultant.mapper.InventoryBillMapper;
import com.example.consultant.pojo.BillInsertParam;
import com.example.consultant.pojo.BillItemInsertParam;
import com.example.consultant.pojo.ToolActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryBillServiceTest {

    @Mock
    private InventoryBillMapper inventoryBillMapper;

    @Mock
    private MaterialService materialService;

    private InventoryBillService inventoryBillService;

    @BeforeEach
    void setUp() {
        inventoryBillService = new InventoryBillService(inventoryBillMapper, materialService, new ObjectMapper());
    }

    @Test
    void createBillShouldAcceptAiFriendlyItemAliases() {
        when(inventoryBillMapper.getCurrentSequenceValue()).thenReturn(1186L);
        doAnswer(invocation -> {
            BillInsertParam param = invocation.getArgument(0);
            param.setId(436L);
            return 1;
        }).when(inventoryBillMapper).insertBillHead(any(BillInsertParam.class));

        String itemsJson = """
                [{
                  "materialId": 696,
                  "unit": "个",
                  "depotId": 26,
                  "number": 10,
                  "purchasePrice": 2000,
                  "totalPrice": 20000,
                  "remark": "智能办公设备初始库存"
                }]
                """;

        ToolActionResult result = inventoryBillService.createBill(
                "入库", "其它入库", 109L, 27L, "2024-12-19", "现结", 168L,
                itemsJson, new BigDecimal("20000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "智能办公本Pro初始库存入库", null
        );

        ArgumentCaptor<BillInsertParam> headCaptor = ArgumentCaptor.forClass(BillInsertParam.class);
        ArgumentCaptor<BillItemInsertParam> itemCaptor = ArgumentCaptor.forClass(BillItemInsertParam.class);
        verify(inventoryBillMapper).insertBillHead(headCaptor.capture());
        verify(inventoryBillMapper).insertBillItem(itemCaptor.capture());

        BillInsertParam savedHead = headCaptor.getValue();
        BillItemInsertParam savedItem = itemCaptor.getValue();

        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("单据创建成功", result.getMessage());
        assertEquals("QTRK00000001186", result.getBusinessNo());
        assertEquals(new BigDecimal("20000"), savedHead.getTotalPrice());
        assertEquals(new BigDecimal("10"), savedItem.getOperNumber());
        assertEquals(new BigDecimal("10"), savedItem.getBasicNumber());
        assertEquals(new BigDecimal("2000"), savedItem.getUnitPrice());
        assertEquals(new BigDecimal("20000"), savedItem.getAllPrice());
        assertEquals("个", savedItem.getMaterialUnit());
        verify(materialService).changeCurrentStock(696L, 26L, new BigDecimal("10"), new BigDecimal("2000"), 160L);
    }

    @Test
    void createBillShouldFailWhenOperNumberMissing() {
        String itemsJson = """
                [{
                  "materialId": 696,
                  "unit": "个",
                  "depotId": 26,
                  "purchasePrice": 2000
                }]
                """;

        ToolActionResult result = inventoryBillService.createBill(
                "入库", "其它入库", 109L, 27L, "2024-12-19", "现结", 168L,
                itemsJson, new BigDecimal("20000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "智能办公本Pro初始库存入库", null
        );

        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("ITEM_OPER_NUMBER_MISSING", result.getErrorCode());
        verify(inventoryBillMapper, never()).insertBillHead(any(BillInsertParam.class));
        verify(inventoryBillMapper, never()).insertBillItem(any(BillItemInsertParam.class));
        verify(materialService, never()).changeCurrentStock(any(), any(), any(), any(), any());
    }

    @Test
    void createOtherInStockForMaterialShouldBuildSingleItemBill() {
        when(inventoryBillMapper.getCurrentSequenceValue()).thenReturn(1187L);
        doAnswer(invocation -> {
            BillInsertParam param = invocation.getArgument(0);
            param.setId(437L);
            return 1;
        }).when(inventoryBillMapper).insertBillHead(any(BillInsertParam.class));

        ToolActionResult result = inventoryBillService.createOtherInStockForMaterial(
                696L, 26L, new BigDecimal("10"), new BigDecimal("2000"),
                109L, 27L, "2024-12-19", "现结", 168L, "初始化库存", null
        );

        ArgumentCaptor<BillItemInsertParam> itemCaptor = ArgumentCaptor.forClass(BillItemInsertParam.class);
        verify(inventoryBillMapper).insertBillItem(itemCaptor.capture());
        BillItemInsertParam savedItem = itemCaptor.getValue();

        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        assertEquals("QTRK00000001187", result.getBusinessNo());
        assertEquals(new BigDecimal("10"), savedItem.getOperNumber());
        assertEquals(new BigDecimal("2000"), savedItem.getUnitPrice());
        assertEquals(new BigDecimal("20000"), savedItem.getAllPrice());
        verify(materialService).changeCurrentStock(696L, 26L, new BigDecimal("10"), new BigDecimal("2000"), 160L);
    }
}
