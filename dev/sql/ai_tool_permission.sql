/*
  AI Tool 权限初始化脚本

  本脚本作用：
  1. 创建 jsh_ai_tool 元数据表
  2. 初始化全部 Tool 方法到 jsh_ai_tool
  3. 基于现有 RoleFunctions 回填 jsh_user_business(type='RoleTools')

  说明：
  - 运行时权限来源是 RoleTools
  - RoleFunctions 仅用于首次初始化/重建 RoleTools
  - Tool 权限粒度固定为 BeanName#methodName
*/

/* =========================================================
   1）创建 AI Tool 元数据表
   ========================================================= */
CREATE TABLE IF NOT EXISTS `jsh_ai_tool` (
  `id` BIGINT NOT NULL COMMENT 'AI Tool 方法ID，使用固定种子值',
  `tool_code` VARCHAR(128) NOT NULL COMMENT '唯一编码，格式：BeanName#methodName',
  `tool_name` VARCHAR(200) NOT NULL COMMENT 'Tool 方法显示名称',
  `bean_name` VARCHAR(100) NOT NULL COMMENT 'Spring Bean/Class 简名',
  `method_name` VARCHAR(100) NOT NULL COMMENT 'Java 方法名',
  `function_ids` VARCHAR(200) DEFAULT NULL COMMENT '关联 ERP 功能ID，格式：[13][14]',
  `registered_in_ai` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已注册进 ConsultantService，1是0否',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用，1启用0禁用',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序字段',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_flag` VARCHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标记，0正常1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_jsh_ai_tool_code` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Tool 方法元数据表';

/* =========================================================
   2）初始化全部 Tool 方法
   ========================================================= */
INSERT INTO `jsh_ai_tool`
(`id`, `tool_code`, `tool_name`, `bean_name`, `method_name`, `function_ids`, `registered_in_ai`, `enabled`, `sort`, `remark`, `delete_flag`)
VALUES
(3001, 'SystemManagementTool#listFunctions', '查询系统功能菜单', 'SystemManagementTool', 'listFunctions', '[16]', 1, 1, 1, NULL, '0'),
(3002, 'SystemManagementTool#searchUsers', '查询系统用户', 'SystemManagementTool', 'searchUsers', '[14]', 1, 1, 2, NULL, '0'),
(3003, 'SystemManagementTool#createUser', '创建系统用户', 'SystemManagementTool', 'createUser', '[14]', 1, 1, 3, NULL, '0'),
(3004, 'SystemManagementTool#updateUserStatus', '更新用户状态', 'SystemManagementTool', 'updateUserStatus', '[14]', 1, 1, 4, NULL, '0'),
(3005, 'SystemManagementTool#searchRoles', '查询角色', 'SystemManagementTool', 'searchRoles', '[13]', 1, 1, 5, NULL, '0'),
(3006, 'SystemManagementTool#searchOrganizations', '查询机构', 'SystemManagementTool', 'searchOrganizations', '[243]', 1, 1, 6, NULL, '0'),
(3007, 'SystemManagementTool#searchHandlers', '查询经手人', 'SystemManagementTool', 'searchHandlers', '[31]', 1, 1, 7, NULL, '0'),
(3008, 'SystemManagementTool#searchAccounts', '查询结算账户', 'SystemManagementTool', 'searchAccounts', '[195]', 1, 1, 8, NULL, '0'),
(3009, 'SystemManagementTool#searchDepots', '查询仓库', 'SystemManagementTool', 'searchDepots', '[26]', 1, 1, 9, NULL, '0'),
(3010, 'SystemManagementTool#searchPartners', '查询供应商客户会员', 'SystemManagementTool', 'searchPartners', '[25][217][218]', 1, 1, 10, NULL, '0'),
(3011, 'SystemManagementTool#searchUnits', '查询计量单位', 'SystemManagementTool', 'searchUnits', '[220]', 1, 1, 11, NULL, '0'),
(3012, 'SystemManagementTool#searchInOutItems', '查询收支项目', 'SystemManagementTool', 'searchInOutItems', '[194]', 1, 1, 12, NULL, '0'),
(3013, 'SystemManagementTool#getSystemConfig', '查询系统参数配置', 'SystemManagementTool', 'getSystemConfig', '[234]', 1, 1, 13, NULL, '0'),
(3014, 'SystemManagementTool#listPlatformConfig', '查询平台参数配置', 'SystemManagementTool', 'listPlatformConfig', '[234]', 1, 1, 14, NULL, '0'),
(3015, 'SystemManagementTool#listMessages', '查询系统消息', 'SystemManagementTool', 'listMessages', '[1]', 1, 1, 15, NULL, '0'),
(3016, 'SystemManagementTool#createMessage', '创建系统消息', 'SystemManagementTool', 'createMessage', '[1]', 1, 1, 16, NULL, '0'),
(3017, 'SystemManagementTool#listLogs', '查询操作日志', 'SystemManagementTool', 'listLogs', '[15]', 1, 1, 17, NULL, '0'),
(3018, 'SystemManagementTool#saveBaseData', '保存基础资料', 'SystemManagementTool', 'saveBaseData', '[13][243][31][195][26][25][217][218][220][194]', 1, 1, 18, NULL, '0'),
(3019, 'SystemManagementTool#updateBaseDataEnabled', '更新基础资料启用状态', 'SystemManagementTool', 'updateBaseDataEnabled', '[13][243][31][195][26][25][217][218][220][194]', 1, 1, 19, NULL, '0'),

(3101, 'MaterialTool#listMaterialCategories', '查询商品分类', 'MaterialTool', 'listMaterialCategories', '[22]', 1, 1, 101, NULL, '0'),
(3102, 'MaterialTool#listMaterialAttributes', '查询商品属性', 'MaterialTool', 'listMaterialAttributes', '[247]', 1, 1, 102, NULL, '0'),
(3103, 'MaterialTool#listMaterialProperties', '查询商品扩展属性', 'MaterialTool', 'listMaterialProperties', '[236]', 1, 1, 103, NULL, '0'),
(3104, 'MaterialTool#searchMaterials', '按名称关键字或分类查找商品', 'MaterialTool', 'searchMaterials', '[23]', 1, 1, 104, NULL, '0'),
(3105, 'MaterialTool#listCurrentMaterials', '查看当前商品清单或者明细', 'MaterialTool', 'listCurrentMaterials', '[23]', 1, 1, 105, NULL, '0'),
(3106, 'MaterialTool#getMaterialDetail', '查询商品详情', 'MaterialTool', 'getMaterialDetail', '[23]', 1, 1, 106, NULL, '0'),
(3107, 'MaterialTool#createMaterial', '创建商品', 'MaterialTool', 'createMaterial', '[23]', 1, 1, 107, NULL, '0'),
(3108, 'MaterialTool#saveMaterialMeta', '保存商品基础资料', 'MaterialTool', 'saveMaterialMeta', '[23]', 1, 1, 108, NULL, '0'),
(3109, 'MaterialTool#updateMaterialPrice', '更新商品默认价格', 'MaterialTool', 'updateMaterialPrice', '[23]', 1, 1, 109, NULL, '0'),
(3110, 'MaterialTool#getMaterialStock', '查询商品库存', 'MaterialTool', 'getMaterialStock', '[246]', 1, 1, 110, NULL, '0'),

(3201, 'InventoryBillTool#listBills', '查询业务单据', 'InventoryBillTool', 'listBills', '[261][241][33][199][242][41][200][210][211][201][202][40][232][233]', 1, 1, 201, NULL, '0'),
(3202, 'InventoryBillTool#getBillDetail', '查询业务单据详情', 'InventoryBillTool', 'getBillDetail', '[261][241][33][199][242][41][200][210][211][201][202][40][232][233]', 1, 1, 202, NULL, '0'),
(3203, 'InventoryBillTool#createBill', '创建业务单据', 'InventoryBillTool', 'createBill', '[261][241][33][199][242][41][200][210][211][201][202][40][232][233]', 1, 1, 203, NULL, '0'),
(3204, 'InventoryBillTool#updateBillStatus', '更新业务单据状态', 'InventoryBillTool', 'updateBillStatus', '[261][241][33][199][242][41][200][210][211][201][202][40][232][233]', 1, 1, 204, NULL, '0'),

(3301, 'FinanceTool#listFinanceRecords', '查询财务单据', 'FinanceTool', 'listFinanceRecords', '[197][203][204][205][206][212]', 1, 1, 301, NULL, '0'),
(3302, 'FinanceTool#getFinanceRecordDetail', '查询财务单据详情', 'FinanceTool', 'getFinanceRecordDetail', '[197][203][204][205][206][212]', 1, 1, 302, NULL, '0'),
(3303, 'FinanceTool#createFinanceRecord', '创建财务单据', 'FinanceTool', 'createFinanceRecord', '[197][203][204][205][206][212]', 1, 1, 303, NULL, '0'),
(3304, 'FinanceTool#updateFinanceStatus', '更新财务单据状态', 'FinanceTool', 'updateFinanceStatus', '[197][203][204][205][206][212]', 1, 1, 304, NULL, '0'),

(3401, 'ReportTool#getDashboardSummary', '查询经营汇总', 'ReportTool', 'getDashboardSummary', '[59]', 1, 1, 401, NULL, '0'),
(3402, 'ReportTool#getSaleStatistics', '查询销售统计', 'ReportTool', 'getSaleStatistics', '[209]', 1, 1, 402, NULL, '0'),
(3403, 'ReportTool#getPurchaseStatistics', '查询采购统计', 'ReportTool', 'getPurchaseStatistics', '[208]', 1, 1, 403, NULL, '0'),
(3404, 'ReportTool#getAccountStatistics', '查询资金统计', 'ReportTool', 'getAccountStatistics', '[207]', 1, 1, 404, NULL, '0'),
(3405, 'ReportTool#getStockWarning', '查询库存预警', 'ReportTool', 'getStockWarning', '[244]', 1, 1, 405, NULL, '0'),
(3406, 'ReportTool#forecastBusinessTrend', '预测业务趋势', 'ReportTool', 'forecastBusinessTrend', '[59][209][208]', 1, 1, 406, NULL, '0'),
(3407, 'ReportTool#forecastCashflowTrend', '预测资金趋势', 'ReportTool', 'forecastCashflowTrend', '[207]', 1, 1, 407, NULL, '0'),
(3408, 'ReportTool#forecastMaterialDemand', '预测商品销量趋势', 'ReportTool', 'forecastMaterialDemand', '[246]', 1, 1, 408, NULL, '0'),

(3501, 'ReservationTool#addReservation', '新增预约填报信息', 'ReservationTool', 'addReservation', NULL, 0, 1, 501, NULL, '0'),
(3502, 'ReservationTool#findByPhone', '根据手机号查询预约信息', 'ReservationTool', 'findByPhone', NULL, 0, 1, 502, NULL, '0')
ON DUPLICATE KEY UPDATE
  `tool_name` = VALUES(`tool_name`),
  `bean_name` = VALUES(`bean_name`),
  `method_name` = VALUES(`method_name`),
  `function_ids` = VALUES(`function_ids`),
  `registered_in_ai` = VALUES(`registered_in_ai`),
  `enabled` = VALUES(`enabled`),
  `sort` = VALUES(`sort`),
  `remark` = VALUES(`remark`),
  `delete_flag` = VALUES(`delete_flag`);

/* =========================================================
   3）基于 RoleFunctions 重建 RoleTools

   这次修复的重点：
   - REGEXP 使用正确模式：\[(13|14|15)\]
   - 旧版本这里反斜杠转义过多，可能导致 JOIN 条件不命中，从而插不进数据
   - GROUP_CONCAT 增加 DISTINCT，避免同一个 Tool ID 被重复拼接
   ========================================================= */

/* 先清空旧的 RoleTools 数据，避免重复 */
DELETE FROM `jsh_user_business`
WHERE `type` = 'RoleTools';

/*
  回填规则：
  - 遍历每一条 RoleFunctions 记录
  - 找出 jsh_ai_tool.function_ids 与当前 RoleFunctions.value 有交集的 Tool
  - 把命中的 jsh_ai_tool.id 聚合成 [id][id][id] 格式，写入 RoleTools
*/
INSERT INTO `jsh_user_business` (`type`, `key_id`, `value`, `btn_str`, `tenant_id`, `delete_flag`)
SELECT
  'RoleTools' AS `type`,
  rf.`key_id`,
  GROUP_CONCAT(DISTINCT CONCAT('[', jt.`id`, ']') ORDER BY jt.`id` SEPARATOR '') AS `value`,
  NULL AS `btn_str`,
  rf.`tenant_id`,
  '0' AS `delete_flag`
FROM `jsh_user_business` rf
JOIN `jsh_ai_tool` jt
  ON jt.`delete_flag` = '0'
 AND jt.`enabled` = 1
 AND jt.`function_ids` IS NOT NULL
 AND jt.`function_ids` <> ''
 AND rf.`value` REGEXP CONCAT(
      '\\[(',
      REPLACE(REPLACE(REPLACE(jt.`function_ids`, '][', '|'), '[', ''), ']', ''),
      ')\\]'
 )
WHERE rf.`type` = 'RoleFunctions'
  AND rf.`delete_flag` = '0'
GROUP BY rf.`key_id`, rf.`tenant_id`;

/* =========================================================
   4）校验 SQL
   需要时手动取消注释执行
   =========================================================

-- 查看初始化后的 Tool 数量
-- SELECT COUNT(*) AS ai_tool_count FROM jsh_ai_tool WHERE delete_flag = '0';

-- 查看回填后的 RoleTools 记录
-- SELECT * FROM jsh_user_business WHERE type = 'RoleTools' ORDER BY tenant_id, key_id;

-- 查看指定角色的 RoleTools
-- SELECT * FROM jsh_user_business WHERE type = 'RoleTools' AND key_id = '1002';

*/
