<!-- 积分商城页:签到卡 + 商品兑换 + 我的/家庭兑换记录(家长可上架/核销) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('points.title') }]" />

    <!-- 签到卡 -->
    <div class="checkin-card card">
      <div class="checkin-info">
        <div class="checkin-balance"><b>{{ stats.balance ?? 0 }}</b><span>{{ $t('points.myPoints') }}</span></div>
        <div class="checkin-streak">{{ $t('points.streak', { n: stats.streak ?? 0 }) }}</div>
      </div>
      <el-button
        type="primary"
        size="large"
        round
        :disabled="stats.checkedToday"
        :loading="checkingIn"
        @click="onCheckin"
      >
        {{ stats.checkedToday ? $t('points.checkedToday') : $t('points.bonus', { n: stats.todayPoints ?? 0 }) }}
      </el-button>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="$t('points.title')" name="shop">
        <div class="list-header">
          <el-button v-if="userStore.isOwner" type="primary" plain @click="openEditor()">{{ $t('points.publish') }}</el-button>
        </div>
        <div v-loading="loading">
          <div v-if="products.length" class="product-grid">
            <div v-for="p in products" :key="p.id" class="product-card card">
              <div class="product-icon">{{ p.icon || '🎁' }}</div>
              <div class="product-name">{{ p.name }}</div>
              <div class="product-points">{{ p.points }} {{ $t('points.points') }}</div>
              <div v-if="p.perLimit > 0" class="product-limit">{{ $t('points.redeemedCount', { count: p.redeemedCount, limit: p.perLimit }) }}</div>
              <div v-if="p.enabled !== 1" class="product-off">{{ $t('points.offShelf') }}</div>
              <div class="product-actions">
                <el-button
                  size="small"
                  type="primary"
                  :disabled="p.enabled !== 1 || soldOut(p)"
                  @click="onRedeem(p)"
                >{{ $t('points.redeem') }}</el-button>
                <el-button v-if="userStore.isOwner" size="small" text @click="openEditor(p)">{{ $t('common.edit') }}</el-button>
                <el-button v-if="userStore.isOwner" size="small" text type="danger" @click="onOff(p)">{{ $t('points.takeOff') }}</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else :description="$t('points.noProducts')" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="userStore.isOwner ? $t('points.familyOrders') : $t('points.myOrders')" name="orders">
        <div v-loading="loadingOrders">
          <el-table v-if="orders.length" :data="orders" stripe>
            <el-table-column prop="productName" :label="$t('points.productName')" min-width="120" />
            <el-table-column v-if="userStore.isOwner" prop="nickname" :label="$t('points.redeemer')" width="100" />
            <el-table-column prop="pointsSpent" :label="$t('points.spent')" width="100" />
            <el-table-column :label="$t('points.time')" width="170">
              <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').slice(0, 16) }}</template>
            </el-table-column>
            <el-table-column :label="$t('points.status')" width="120">
              <template #default="{ row }">
                <el-tag :type="row.status === 'REDEEMED' ? 'success' : 'warning'">
                  {{ row.status === 'REDEEMED' ? $t('points.fulfilled') : $t('points.awaiting') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="userStore.isOwner" :label="$t('common.actions')" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" size="small" type="success" plain @click="onTaken(row)">{{ $t('points.taken') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="$t('points.noOrders')" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 上架/编辑商品弹窗 -->
    <el-dialog v-model="editor.visible" append-to-body :title="editor.form.id ? $t('points.editProduct') : $t('points.publish')" width="420px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('points.productName')">
          <el-input v-model="editor.form.name" :placeholder="$t('points.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('points.icon')">
          <el-input v-model="editor.form.icon" :placeholder="$t('points.iconPlaceholder')" maxlength="4" />
        </el-form-item>
        <el-form-item :label="$t('points.points')">
          <el-input-number v-model="editor.form.points" :min="1" />
        </el-form-item>
        <div class="form-row">
          <el-form-item :label="$t('points.stockHint')">
            <el-input-number v-model="editor.form.stock" :min="-1" />
          </el-form-item>
          <el-form-item :label="$t('points.perLimitHint')">
            <el-input-number v-model="editor.form.perLimit" :min="0" />
          </el-form-item>
        </div>
        <el-form-item :label="$t('points.publishStatus')">
          <el-switch v-model="editor.form.enabled" :active-value="1" :inactive-value="0" :active-text="$t('points.onSale')" :inactive-text="$t('points.takeOff')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 积分商城:签到、家庭商品兑换;家长可上架/编辑/下架商品并核销兑换
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pointsApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const userStore = useUserStore()

const stats = ref({})
const checkingIn = ref(false)
const loading = ref(false)
const loadingOrders = ref(false)
const products = ref([])
const orders = ref([])
const activeTab = ref('shop')
const saving = ref(false)

const editor = reactive({ visible: false, form: {} })

/** 商品是否不可兑:已下架/已兑完/已达限兑次数 */
const soldOut = (p) =>
  p.enabled !== 1 || (p.stock === 0) || (p.perLimit > 0 && p.redeemedCount >= p.perLimit)

const loadStats = async () => {
  stats.value = await pointsApi.stats()
}

const loadProducts = async () => {
  loading.value = true
  try {
    products.value = await pointsApi.products()
  } finally {
    loading.value = false
  }
}

const loadOrders = async () => {
  loadingOrders.value = true
  try {
    orders.value = userStore.isOwner ? await pointsApi.familyOrders() : await pointsApi.myOrders()
  } finally {
    loadingOrders.value = false
  }
}

// 签到:成功后刷新概览与流水
const onCheckin = async () => {
  checkingIn.value = true
  try {
    const r = await pointsApi.checkin()
    ElMessage.success(t('points.checkinSuccess', { points: r.points, streak: r.streak }))
    await loadStats()
  } finally {
    checkingIn.value = false
  }
}

// 兑换确认 -> 落单后刷新余额/商品/订单
const onRedeem = async (p) => {
  await ElMessageBox.confirm(t('points.redeemMessage', { points: p.points, item: p.name }), t('points.redeemTitle'), { type: 'warning', closeOnClickModal: true })
  await pointsApi.redeem(p.id)
  ElMessage.success(t('points.redeemSuccess'))
  await Promise.all([loadStats(), loadProducts(), loadOrders()])
}

const openEditor = (p) => {
  editor.visible = true
  editor.form = p
    ? { id: p.id, name: p.name, icon: p.icon || '', points: p.points, stock: p.stock, perLimit: p.perLimit, enabled: p.enabled }
    : { id: null, name: '', icon: '', points: 10, stock: -1, perLimit: 1, enabled: 1 }
}

const onSave = async () => {
  saving.value = true
  try {
    const { id, ...data } = editor.form
    if (id) {
      await pointsApi.update(id, data)
    } else {
      await pointsApi.create(data)
    }
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    await loadProducts()
  } finally {
    saving.value = false
  }
}

const onOff = async (p) => {
  await ElMessageBox.confirm(t('points.offMessage', { item: p.name }), t('points.offTitle'), { type: 'warning', closeOnClickModal: true })
  await pointsApi.remove(p.id)
  ElMessage.success(t('points.offShelf'))
  await loadProducts()
}

const onTaken = async (row) => {
  await pointsApi.markTaken(row.id)
  ElMessage.success(t('points.fulfilled'))
  await loadOrders()
}

onMounted(async () => {
  await Promise.all([loadStats(), loadProducts()])
})
</script>

<style scoped>
/* 签到卡:左侧积分/连续信息,右侧签到按钮 */
.checkin-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ffb74d, #ff8a65);
  color: #fff;
  margin-bottom: 20px;
}
.checkin-info {
  display: flex;
  gap: 28px;
  align-items: baseline;
}
.checkin-balance b {
  font-size: 32px;
  margin-right: 6px;
}
.checkin-balance span {
  opacity: 0.9;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.product-card {
  padding: 18px;
  text-align: center;
}
.product-icon {
  font-size: 36px;
}
.product-name {
  font-weight: 600;
  margin: 8px 0 4px;
}
.product-points {
  color: #e6a23c;
  font-weight: 600;
}
.product-limit,
.product-off {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
.product-actions {
  margin-top: 12px;
}
.form-row {
  display: flex;
  gap: 12px;
}
.form-row .el-form-item {
  flex: 1;
}

@media (max-width: 768px) {
  .form-row { flex-direction: column; gap: 0; }
  .product-grid { grid-template-columns: repeat(2, 1fr) !important; }
}
</style>