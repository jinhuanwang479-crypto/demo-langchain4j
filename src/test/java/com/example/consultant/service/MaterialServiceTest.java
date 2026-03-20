package com.example.consultant.service;

import com.example.consultant.mapper.MaterialMapper;
import com.example.consultant.mapper.SystemManagementMapper;
import com.example.consultant.pojo.ErpUnit;
import com.example.consultant.pojo.MaterialInsertParam;
import com.example.consultant.pojo.ToolActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialMapper materialMapper;

    @Mock
    private SystemManagementMapper systemManagementMapper;

    private MaterialService materialService;

    @BeforeEach
    void setUp() {
        materialService = new MaterialService(materialMapper, systemManagementMapper, new ObjectMapper());
    }

    @Test
    void createMaterialShouldFailWhenCategoryIdMissing() {
        ToolActionResult result = materialService.createMaterial("测试商品", null, "kg", 25L,
                null, null, null, null, null, null);

        assertEquals("商品分类不能为空，请先查询并确认分类", result.getMessage());
        verify(materialMapper, never()).insertMaterial(any());
    }

    @Test
    void createMaterialShouldFailWhenUnitMissing() {
        ToolActionResult result = materialService.createMaterial("测试商品", 100L, null, 25L,
                null, null, null, null, null, null);

        assertEquals("商品单位不能为空，请先查询并确认单位", result.getMessage());
        verify(materialMapper, never()).insertMaterial(any());
    }

    @Test
    void createMaterialShouldFailWhenUnitIdMissing() {
        ToolActionResult result = materialService.createMaterial("测试商品", 100L, "kg", null,
                null, null, null, null, null, null);

        assertEquals("商品单位ID不能为空，请先查询并确认单位", result.getMessage());
        verify(materialMapper, never()).insertMaterial(any());
    }

    @Test
    void createMaterialShouldFailWhenCategoryNotFound() {
        when(materialMapper.countMaterialCategoryById(100L, 160L)).thenReturn(0);

        ToolActionResult result = materialService.createMaterial("测试商品", 100L, "kg", 25L,
                null, null, null, null, null, null);

        assertEquals("未找到对应的商品分类，请重新确认分类", result.getMessage());
        verify(materialMapper, never()).insertMaterial(any());
    }

    @Test
    void createMaterialShouldFailWhenUnitNotFound() {
        when(materialMapper.countMaterialCategoryById(100L, 160L)).thenReturn(1);
        when(systemManagementMapper.getUnitById(25L, 160L)).thenReturn(null);

        ToolActionResult result = materialService.createMaterial("测试商品", 100L, "kg", 25L,
                null, null, null, null, null, null);

        assertEquals("未找到对应的计量单位，请重新确认单位", result.getMessage());
        verify(materialMapper, never()).insertMaterial(any());
    }

    @Test
    void createMaterialShouldFailWhenUnitValueDoesNotBelongToUnitDefinition() {
        when(materialMapper.countMaterialCategoryById(100L, 160L)).thenReturn(1);
        when(systemManagementMapper.getUnitById(25L, 160L)).thenReturn(unit("kg/箱", "kg", "箱", null, null));

        ToolActionResult result = materialService.createMaterial("测试商品", 100L, "支", 25L,
                null, null, null, null, null, null);

        assertEquals("商品单位必须是所选计量单位下的实际单位值", result.getMessage());
        verify(materialMapper, never()).insertMaterial(any());
    }

    @Test
    void createMaterialShouldInsertWhenCategoryAndUnitAreValid() {
        when(materialMapper.countMaterialCategoryById(100L, 160L)).thenReturn(1);
        when(systemManagementMapper.getUnitById(25L, 160L)).thenReturn(unit("kg/箱", "kg", "箱", null, null));
        doAnswer(invocation -> {
            MaterialInsertParam param = invocation.getArgument(0);
            param.setId(999L);
            return 1;
        }).when(materialMapper).insertMaterial(any(MaterialInsertParam.class));

        ToolActionResult result = materialService.createMaterial(" 测试商品 ", 100L, " kg ", 25L,
                "M1", "S1", "品牌A", "CS", "备注", null);

        ArgumentCaptor<MaterialInsertParam> captor = ArgumentCaptor.forClass(MaterialInsertParam.class);
        verify(materialMapper).insertMaterial(captor.capture());
        MaterialInsertParam saved = captor.getValue();

        assertEquals("商品创建成功", result.getMessage());
        assertEquals(999L, result.getAffectedId());
        assertEquals(100L, saved.getCategoryId());
        assertEquals(25L, saved.getUnitId());
        assertEquals("kg", saved.getUnit());
        assertEquals("测试商品", saved.getName());
        assertEquals(160L, saved.getTenantId());
        assertTrue(saved.getEnabled() == 1);
        assertNull(result.getBusinessNo());
    }

    private ErpUnit unit(String name, String basicUnit, String otherUnit, String otherUnitTwo, String otherUnitThree) {
        ErpUnit erpUnit = new ErpUnit();
        erpUnit.setName(name);
        erpUnit.setBasicUnit(basicUnit);
        erpUnit.setOtherUnit(otherUnit);
        erpUnit.setOtherUnitTwo(otherUnitTwo);
        erpUnit.setOtherUnitThree(otherUnitThree);
        return erpUnit;
    }
}
