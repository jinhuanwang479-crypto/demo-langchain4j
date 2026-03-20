<template>
    <div class="ai-monitor-page">
        <div class="hero-panel">
            <div class="hero-copy">
                <div class="hero-eyebrow">AI 助手评估与监测中心</div>
                <h1>把每一次问答、检索、工具调用和评分都看清楚</h1>
                <p>这个页面直接对接新后端监控接口，用来查看请求概览、筛选追踪、打开详情、排查异常。</p>
            </div>
            <div class="hero-actions">
                <a-button type="primary" icon="sync" :loading="summaryLoading || tableLoading" @click="refreshAll">
                    刷新数据
                </a-button>
                <a-button icon="reload" @click="resetFilters">
                    重置筛选
                </a-button>
            </div>
        </div>

        <a-row :gutter="16" class="summary-row">
            <a-col :xs="24" :sm="12" :lg="6" v-for="card in summaryCards" :key="card.key">
                <div class="summary-card" :class="card.key">
                    <div class="summary-label">{{ card.label }}</div>
                    <div class="summary-value">{{ card.value }}</div>
                    <div class="summary-hint">{{ card.hint }}</div>
                </div>
            </a-col>
        </a-row>

        <div class="filters-panel">
            <div class="panel-title">筛选条件</div>
            <a-row :gutter="16">
                <a-col :xs="24" :md="12" :lg="6">
                    <div class="filter-item">
                        <label>时间范围</label>
                        <a-range-picker
                            v-model="timeRange"
                            show-time
                            format="YYYY-MM-DD HH:mm:ss"
                            style="width: 100%"
                        />
                    </div>
                </a-col>
                <a-col :xs="24" :md="12" :lg="4">
                    <div class="filter-item">
                        <label>memoryId</label>
                        <a-input v-model="filters.memoryId" placeholder="输入会话 memoryId" />
                    </div>
                </a-col>
                <a-col :xs="24" :md="12" :lg="4">
                    <div class="filter-item">
                        <label>userId</label>
                        <a-input v-model="filters.userId" placeholder="输入用户 ID" />
                    </div>
                </a-col>
                <a-col :xs="24" :md="12" :lg="4">
                    <div class="filter-item">
                        <label>请求状态</label>
                        <a-select v-model="filters.status" allowClear placeholder="全部状态">
                            <a-select-option value="SUCCESS">SUCCESS</a-select-option>
                            <a-select-option value="ERROR">ERROR</a-select-option>
                        </a-select>
                    </div>
                </a-col>
                <a-col :xs="24" :md="12" :lg="3">
                    <div class="filter-item">
                        <label>风险等级</label>
                        <a-select v-model="filters.riskLevel" allowClear placeholder="全部等级">
                            <a-select-option value="LOW">LOW</a-select-option>
                            <a-select-option value="MEDIUM">MEDIUM</a-select-option>
                            <a-select-option value="HIGH">HIGH</a-select-option>
                        </a-select>
                    </div>
                </a-col>
                <a-col :xs="24" :md="12" :lg="3">
                    <div class="filter-item actions">
                        <label>操作</label>
                        <div class="filter-btns">
                            <a-button type="primary" @click="handleSearch" :loading="tableLoading">查询</a-button>
                            <a-button @click="resetFilters">清空</a-button>
                        </div>
                    </div>
                </a-col>
            </a-row>
        </div>

        <div class="table-panel">
            <div class="panel-head">
                <div>
                    <div class="panel-title">请求追踪列表</div>
                    <div class="panel-subtitle">展示每一次 AI 请求的状态、耗时、检索结果和自动评估结果。</div>
                </div>
                <div class="panel-tip">总数：{{ pagination.total }}</div>
            </div>

            <a-table
                rowKey="requestId"
                :columns="columns"
                :data-source="tableData"
                :loading="tableLoading"
                :pagination="pagination"
                :scroll="{ x: 1380 }"
                @change="handleTableChange"
            >
                <template slot="status" slot-scope="text">
                    <a-tag :color="text === 'SUCCESS' ? 'green' : 'red'">
                        {{ text || '-' }}
                    </a-tag>
                </template>

                <template slot="evaluationStatus" slot-scope="text">
                    <a-tag :color="evaluationTagColor(text)">
                        {{ text || '-' }}
                    </a-tag>
                </template>

                <template slot="riskLevel" slot-scope="text">
                    <a-tag :color="riskTagColor(text)">
                        {{ text || '-' }}
                    </a-tag>
                </template>

                <template slot="latencyMs" slot-scope="text, record">
                    <div class="metric-cell">
                        <div>{{ formatNumber(record.latencyMs) }} ms</div>
                        <span>首字：{{ formatNumber(record.firstTokenLatencyMs) }} ms</span>
                    </div>
                </template>

                <template slot="retrieval" slot-scope="text, record">
                    <div class="metric-cell">
                        <div>命中：{{ record.retrievedCount || 0 }}</div>
                        <span>
                            Top：{{ record.topRetrievalScore == null ? '-' : Number(record.topRetrievalScore).toFixed(3) }}
                        </span>
                    </div>
                </template>

                <template slot="question" slot-scope="text">
                    <div class="ellipsis-cell" :title="text">{{ text || '-' }}</div>
                </template>

                <template slot="actions" slot-scope="text, record">
                    <a-button type="link" @click="openDetail(record)">查看详情</a-button>
                </template>
            </a-table>
        </div>

        <a-drawer
            title="请求追踪详情"
            placement="right"
            :visible="detailVisible"
            :width="720"
            @close="closeDetail"
        >
            <a-spin :spinning="detailLoading">
                <template v-if="detail.requestTrace">
                    <div class="detail-section">
                        <div class="detail-title">基础信息</div>
                        <a-descriptions :column="2" bordered size="small">
                            <a-descriptions-item label="requestId">{{ detail.requestTrace.requestId || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="memoryId">{{ detail.requestTrace.memoryId || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="tenantId">{{ detail.requestTrace.tenantId || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="userId">{{ detail.requestTrace.userId || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="状态">{{ detail.requestTrace.status || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="完成原因">{{ detail.requestTrace.finishReason || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="耗时">{{ formatNumber(detail.requestTrace.latencyMs) }} ms</a-descriptions-item>
                            <a-descriptions-item label="首字耗时">{{ formatNumber(detail.requestTrace.firstTokenLatencyMs) }} ms</a-descriptions-item>
                            <a-descriptions-item label="输入 Token">{{ formatNumber(detail.requestTrace.inputTokens) }}</a-descriptions-item>
                            <a-descriptions-item label="输出 Token">{{ formatNumber(detail.requestTrace.outputTokens) }}</a-descriptions-item>
                            <a-descriptions-item label="总 Token">{{ formatNumber(detail.requestTrace.totalTokens) }}</a-descriptions-item>
                            <a-descriptions-item label="工具调用次数">{{ formatNumber(detail.requestTrace.toolCallCount) }}</a-descriptions-item>
                            <a-descriptions-item label="评估分数">{{ formatNumber(detail.requestTrace.evaluationScore) }}</a-descriptions-item>
                            <a-descriptions-item label="评估状态">{{ detail.requestTrace.evaluationStatus || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="风险等级">{{ detail.requestTrace.riskLevel || '-' }}</a-descriptions-item>
                            <a-descriptions-item label="检索拒答原因">{{ detail.requestTrace.retrievalRejectedReason || '-' }}</a-descriptions-item>
                        </a-descriptions>
                    </div>

                    <div class="detail-section">
                        <div class="detail-title">问题与回答</div>
                        <div class="text-block">
                            <div class="block-label">问题</div>
                            <div class="block-body">{{ detail.requestTrace.question || '-' }}</div>
                        </div>
                        <div class="text-block">
                            <div class="block-label">回答</div>
                            <div class="block-body answer">{{ detail.requestTrace.response || '-' }}</div>
                        </div>
                    </div>

                    <div class="detail-section">
                        <div class="detail-title">评估原因</div>
                        <div class="tag-group" v-if="evaluationReasons.length">
                            <a-tag v-for="item in evaluationReasons" :key="item" color="blue">{{ item }}</a-tag>
                        </div>
                        <div class="empty-text" v-else>暂无评估原因</div>
                    </div>

                    <div class="detail-section">
                        <div class="detail-title">检索快照</div>
                        <a-table
                            size="small"
                            rowKey="__rowKey"
                            :pagination="false"
                            :data-source="retrievalSnapshots"
                            :columns="retrievalColumns"
                            :scroll="{ x: 640 }"
                        >
                            <template slot="snapshotText" slot-scope="text">
                                <div class="ellipsis-cell multi" :title="text">{{ text || '-' }}</div>
                            </template>
                        </a-table>
                    </div>

                    <div class="detail-section">
                        <div class="detail-title">工具调用明细</div>
                        <a-table
                            size="small"
                            rowKey="sequenceNo"
                            :pagination="false"
                            :data-source="detail.toolTraces || []"
                            :columns="toolColumns"
                            :scroll="{ x: 640 }"
                        >
                            <template slot="toolSuccess" slot-scope="text">
                                <a-tag :color="text ? 'green' : 'red'">
                                    {{ text ? '成功' : '失败' }}
                                </a-tag>
                            </template>
                            <template slot="toolText" slot-scope="text">
                                <div class="ellipsis-cell multi" :title="text">{{ text || '-' }}</div>
                            </template>
                        </a-table>
                    </div>
                </template>
            </a-spin>
        </a-drawer>
    </div>
</template>

<script>
import {
    getAiObservationDetail,
    getAiObservationList,
    getAiObservationSummary
} from '@/api/aoi'

export default {
    name: 'AiObservationDashboard',
    data() {
        return {
            summaryLoading: false,
            tableLoading: false,
            detailLoading: false,
            detailVisible: false,
            filters: {
                memoryId: '',
                userId: '',
                status: undefined,
                riskLevel: undefined
            },
            timeRange: [],
            summary: {
                totalRequests: 0,
                errorRate: 0,
                averageLatencyMs: 0,
                p95LatencyMs: 0,
                averageScore: 0,
                passCount: 0,
                warnCount: 0,
                failCount: 0
            },
            tableData: [],
            detail: {
                requestTrace: null,
                toolTraces: []
            },
            pagination: {
                current: 1,
                pageSize: 10,
                total: 0,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: total => `共 ${total} 条`
            },
            columns: [
                {
                    title: 'requestId',
                    dataIndex: 'requestId',
                    width: 220
                },
                {
                    title: '状态',
                    dataIndex: 'status',
                    width: 110,
                    scopedSlots: { customRender: 'status' }
                },
                {
                    title: '评估状态',
                    dataIndex: 'evaluationStatus',
                    width: 120,
                    scopedSlots: { customRender: 'evaluationStatus' }
                },
                {
                    title: '风险等级',
                    dataIndex: 'riskLevel',
                    width: 120,
                    scopedSlots: { customRender: 'riskLevel' }
                },
                {
                    title: '耗时',
                    width: 140,
                    scopedSlots: { customRender: 'latencyMs' }
                },
                {
                    title: '检索',
                    width: 140,
                    scopedSlots: { customRender: 'retrieval' }
                },
                {
                    title: '工具次数',
                    dataIndex: 'toolCallCount',
                    width: 100
                },
                {
                    title: '评分',
                    dataIndex: 'evaluationScore',
                    width: 90
                },
                {
                    title: '问题',
                    dataIndex: 'question',
                    scopedSlots: { customRender: 'question' }
                },
                {
                    title: '操作',
                    width: 100,
                    fixed: 'right',
                    scopedSlots: { customRender: 'actions' }
                }
            ],
            retrievalColumns: [
                {
                    title: '文档',
                    dataIndex: 'docName',
                    width: 140
                },
                {
                    title: '页码',
                    dataIndex: 'pageNumber',
                    width: 80
                },
                {
                    title: '分块',
                    dataIndex: 'chunkIndex',
                    width: 80
                },
                {
                    title: '分数',
                    dataIndex: 'score',
                    width: 90
                },
                {
                    title: '片段预览',
                    dataIndex: 'textPreview',
                    scopedSlots: { customRender: 'snapshotText' }
                }
            ],
            toolColumns: [
                {
                    title: '序号',
                    dataIndex: 'sequenceNo',
                    width: 70
                },
                {
                    title: '工具名',
                    dataIndex: 'toolName',
                    width: 130
                },
                {
                    title: '状态',
                    dataIndex: 'success',
                    width: 90,
                    scopedSlots: { customRender: 'toolSuccess' }
                },
                {
                    title: '参数',
                    dataIndex: 'argumentsJson',
                    scopedSlots: { customRender: 'toolText' }
                },
                {
                    title: '结果预览',
                    dataIndex: 'resultPreview',
                    scopedSlots: { customRender: 'toolText' }
                }
            ]
        }
    },
    computed: {
        summaryCards() {
            return [
                {
                    key: 'requests',
                    label: '总请求数',
                    value: this.formatNumber(this.summary.totalRequests),
                    hint: '当前时间窗口内累计请求'
                },
                {
                    key: 'latency',
                    label: '平均耗时',
                    value: `${this.formatDecimal(this.summary.averageLatencyMs)} ms`,
                    hint: `P95：${this.formatDecimal(this.summary.p95LatencyMs)} ms`
                },
                {
                    key: 'score',
                    label: '平均评分',
                    value: this.formatDecimal(this.summary.averageScore),
                    hint: `错误率：${this.formatPercent(this.summary.errorRate)}`
                },
                {
                    key: 'status',
                    label: '评估分布',
                    value: `${this.summary.passCount}/${this.summary.warnCount}/${this.summary.failCount}`,
                    hint: 'PASS / WARN / FAIL'
                }
            ]
        },
        evaluationReasons() {
            return this.parseJsonArray(this.detail.requestTrace && this.detail.requestTrace.evaluationReasonsJson)
        },
        retrievalSnapshots() {
            return this.parseJsonArray(this.detail.requestTrace && this.detail.requestTrace.retrievalSnapshotJson)
                .map((item, index) => Object.assign({ __rowKey: index + 1 }, item))
        }
    },
    mounted() {
        this.refreshAll()
    },
    methods: {
        refreshAll() {
            this.loadSummary()
            this.loadTable()
        },
        handleSearch() {
            this.pagination.current = 1
            this.refreshAll()
        },
        handleTableChange(pagination) {
            this.pagination.current = pagination.current
            this.pagination.pageSize = pagination.pageSize
            this.loadTable()
        },
        resetFilters() {
            this.filters = {
                memoryId: '',
                userId: '',
                status: undefined,
                riskLevel: undefined
            }
            this.timeRange = []
            this.pagination.current = 1
            this.refreshAll()
        },
        loadSummary() {
            this.summaryLoading = true
            getAiObservationSummary(this.buildTimeParams())
                .then(res => {
                    if (res && res.success) {
                        this.summary = Object.assign({}, this.summary, res.result || {})
                        return
                    }
                    this.$message.error((res && res.message) || '获取监控汇总失败')
                })
                .catch(() => {
                    this.$message.error('获取监控汇总失败')
                })
                .finally(() => {
                    this.summaryLoading = false
                })
        },
        loadTable() {
            this.tableLoading = true
            const params = Object.assign({}, this.buildTimeParams(), {
                memoryId: this.filters.memoryId || undefined,
                userId: this.filters.userId || undefined,
                status: this.filters.status || undefined,
                riskLevel: this.filters.riskLevel || undefined,
                page: this.pagination.current,
                size: this.pagination.pageSize
            })
            getAiObservationList(params)
                .then(res => {
                    if (res && res.success) {
                        const payload = res.result || {}
                        this.tableData = payload.items || []
                        this.pagination.total = payload.total || 0
                        return
                    }
                    this.$message.error((res && res.message) || '获取监控列表失败')
                })
                .catch(() => {
                    this.$message.error('获取监控列表失败')
                })
                .finally(() => {
                    this.tableLoading = false
                })
        },
        openDetail(record) {
            if (!record || !record.requestId) {
                return
            }
            this.detailVisible = true
            this.detailLoading = true
            getAiObservationDetail(record.requestId)
                .then(res => {
                    if (res && res.success) {
                        this.detail = res.result || { requestTrace: null, toolTraces: [] }
                        return
                    }
                    this.$message.error((res && res.message) || '获取请求详情失败')
                })
                .catch(() => {
                    this.$message.error('获取请求详情失败')
                })
                .finally(() => {
                    this.detailLoading = false
                })
        },
        closeDetail() {
            this.detailVisible = false
            this.detail = {
                requestTrace: null,
                toolTraces: []
            }
        },
        buildTimeParams() {
            const params = {}
            if (this.timeRange && this.timeRange.length === 2) {
                params.startTime = this.formatDateTime(this.timeRange[0])
                params.endTime = this.formatDateTime(this.timeRange[1])
            }
            return params
        },
        formatDateTime(value) {
            if (!value) {
                return undefined
            }
            if (typeof value.format === 'function') {
                return value.format('YYYY-MM-DDTHH:mm:ss')
            }
            return value
        },
        parseJsonArray(value) {
            if (!value) {
                return []
            }
            try {
                const parsed = JSON.parse(value)
                return Array.isArray(parsed) ? parsed : []
            } catch (e) {
                return []
            }
        },
        evaluationTagColor(status) {
            if (status === 'PASS') return 'green'
            if (status === 'WARN') return 'orange'
            if (status === 'FAIL') return 'red'
            return 'default'
        },
        riskTagColor(level) {
            if (level === 'LOW') return 'blue'
            if (level === 'MEDIUM') return 'orange'
            if (level === 'HIGH') return 'red'
            return 'default'
        },
        formatNumber(value) {
            return value == null || value === '' ? '-' : value
        },
        formatDecimal(value) {
            return value == null || value === '' ? '0' : Number(value).toFixed(2)
        },
        formatPercent(value) {
            if (value == null || value === '') {
                return '0.00%'
            }
            return `${(Number(value) * 100).toFixed(2)}%`
        }
    }
}
</script>

<style scoped>
.ai-monitor-page {
    min-height: 100%;
    padding: 24px;
    background:
        radial-gradient(circle at top left, rgba(12, 108, 242, 0.16), transparent 30%),
        radial-gradient(circle at top right, rgba(22, 163, 74, 0.12), transparent 28%),
        linear-gradient(180deg, #f5f9ff 0%, #eef4fb 48%, #f7fafc 100%);
}

.hero-panel,
.filters-panel,
.table-panel {
    background: rgba(255, 255, 255, 0.92);
    border: 1px solid rgba(208, 220, 234, 0.9);
    border-radius: 24px;
    box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.hero-panel {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    align-items: flex-start;
    padding: 28px 32px;
    margin-bottom: 20px;
}

.hero-eyebrow {
    display: inline-flex;
    align-items: center;
    padding: 6px 12px;
    border-radius: 999px;
    background: rgba(15, 118, 110, 0.1);
    color: #0f766e;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 1px;
}

.hero-copy h1 {
    margin: 14px 0 10px;
    color: #10243e;
    font-size: 32px;
    line-height: 1.25;
    font-weight: 700;
}

.hero-copy p {
    margin: 0;
    max-width: 720px;
    color: #4b5d73;
    font-size: 15px;
    line-height: 1.8;
}

.hero-actions {
    display: flex;
    gap: 12px;
    flex-shrink: 0;
}

.summary-row {
    margin-bottom: 20px;
}

.summary-card {
    height: 100%;
    min-height: 148px;
    padding: 22px 24px;
    border-radius: 22px;
    color: #fff;
    overflow: hidden;
    position: relative;
    box-shadow: 0 16px 36px rgba(15, 23, 42, 0.12);
}

.summary-card::after {
    content: '';
    position: absolute;
    right: -32px;
    top: -32px;
    width: 112px;
    height: 112px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
}

.summary-card.requests {
    background: linear-gradient(135deg, #2563eb, #1d4ed8);
}

.summary-card.latency {
    background: linear-gradient(135deg, #0891b2, #0f766e);
}

.summary-card.score {
    background: linear-gradient(135deg, #7c3aed, #6d28d9);
}

.summary-card.status {
    background: linear-gradient(135deg, #ea580c, #c2410c);
}

.summary-label {
    position: relative;
    z-index: 1;
    font-size: 13px;
    letter-spacing: 0.5px;
    opacity: 0.88;
}

.summary-value {
    position: relative;
    z-index: 1;
    margin-top: 14px;
    font-size: 34px;
    font-weight: 700;
    line-height: 1.1;
}

.summary-hint {
    position: relative;
    z-index: 1;
    margin-top: 12px;
    font-size: 13px;
    opacity: 0.86;
}

.filters-panel,
.table-panel {
    padding: 22px 24px;
    margin-bottom: 20px;
}

.panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 18px;
}

.panel-title {
    color: #10243e;
    font-size: 18px;
    font-weight: 700;
    margin-bottom: 4px;
}

.panel-subtitle,
.panel-tip {
    color: #6b7c93;
    font-size: 13px;
}

.filter-item label {
    display: block;
    margin-bottom: 8px;
    color: #425466;
    font-size: 13px;
    font-weight: 600;
}

.filter-item.actions .filter-btns {
    display: flex;
    gap: 8px;
}

.metric-cell {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.metric-cell span {
    color: #7a8a9e;
    font-size: 12px;
}

.ellipsis-cell {
    max-width: 320px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.ellipsis-cell.multi {
    max-width: 100%;
    white-space: normal;
    word-break: break-word;
    line-height: 1.7;
}

.detail-section {
    margin-bottom: 24px;
}

.detail-title {
    margin-bottom: 12px;
    color: #10243e;
    font-size: 16px;
    font-weight: 700;
}

.text-block {
    margin-bottom: 12px;
    padding: 14px 16px;
    border-radius: 16px;
    background: #f8fbff;
    border: 1px solid #e3edf8;
}

.block-label {
    margin-bottom: 8px;
    color: #4f6b88;
    font-size: 12px;
    font-weight: 700;
}

.block-body {
    color: #20344a;
    line-height: 1.8;
    white-space: pre-wrap;
    word-break: break-word;
}

.block-body.answer {
    max-height: 240px;
    overflow: auto;
}

.tag-group {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.empty-text {
    color: #8a99ad;
}

@media (max-width: 992px) {
    .ai-monitor-page {
        padding: 16px;
    }

    .hero-panel {
        flex-direction: column;
        align-items: stretch;
    }

    .hero-actions {
        width: 100%;
        justify-content: flex-start;
        flex-wrap: wrap;
    }
}
</style>
