import request from '@/utils/request'

// 认证
export const login = (data) => request.post('/auth/login', data)
export const loginByCode = (data) => request.post('/auth/login-by-code', data)
export const sendCaptcha = (data) => request.post('/auth/captcha', data)
export const register = (data) => request.post('/auth/register', data)
export const resetPassword = (data) => request.post('/auth/reset-password', data)
export const logout = () => request.post('/auth/logout')
export const getInfo = () => request.get('/auth/info')

// 用户
export const userPage = (params) => request.get('/user/page', { params })
export const userDetail = (id) => request.get(`/user/${id}`)
export const userAdd = (data) => request.post('/user', data)
export const userEdit = (data) => request.put('/user', data)
export const userDelete = (id) => request.delete(`/user/${id}`)
export const userResetPwd = (id, password) =>
  request.put(`/user/${id}/reset-pwd`, null, { params: { password } })
export const userStatus = (id, status) =>
  request.put(`/user/${id}/status`, null, { params: { status } })
// 审核通过：可同时提交为该注册用户分配的角色（不传则沿用注册时预绑的默认角色）
export const userApprove = (id, roleIds) =>
  request.put(`/user/${id}/approve`, roleIds || [])
// 用户下拉选项（精简字段，供选择实验室负责人 / 消息接收人，无需用户管理权限）
export const userOptions = () => request.get('/user/options')
export const userAssignRoles = (id, roleIds) =>
  request.put(`/user/${id}/roles`, roleIds)

// 角色
export const rolePage = (params) => request.get('/role/page', { params })
export const roleList = () => request.get('/role/list')
export const roleAdd = (data) => request.post('/role', data)
export const roleEdit = (data) => request.put('/role', data)
export const roleDelete = (id) => request.delete(`/role/${id}`)
export const roleStatus = (id, status) =>
  request.put(`/role/${id}/status`, null, { params: { status } })
export const rolePerms = (id) => request.get(`/role/${id}/perms`)
export const roleAssignPerms = (id, permIds) =>
  request.put(`/role/${id}/perms`, permIds)

// 权限
export const permTree = () => request.get('/permission/tree')
export const permAdd = (data) => request.post('/permission', data)
export const permEdit = (data) => request.put('/permission', data)
export const permDelete = (id) => request.delete(`/permission/${id}`)

// 实验室
export const labPage = (params) => request.get('/lab/page', { params })
export const labList = () => request.get('/lab/list')
export const labDetail = (id) => request.get(`/lab/${id}`)
export const labAdd = (data) => request.post('/lab', data)
export const labEdit = (data) => request.put('/lab', data)
export const labDelete = (id) => request.delete(`/lab/${id}`)
export const labOpen = (id, openStatus) =>
  request.put(`/lab/${id}/open`, null, { params: { openStatus } })
export const labReserve = (id, enableReserve) =>
  request.put(`/lab/${id}/reserve`, null, { params: { enableReserve } })

// 开放时段
export const slotList = (labId) => request.get(`/lab/slot/${labId}`)
export const slotSave = (labId, data) => request.post(`/lab/slot/${labId}`, data)
export const slotAvailable = (labId, date) =>
  request.get(`/lab/slot/available/${labId}`, { params: { date } })

// 设备
export const devicePage = (params) => request.get('/device/page', { params })
export const deviceDetail = (id) => request.get(`/device/${id}`)
export const deviceAdd = (data) => request.post('/device', data)
export const deviceEdit = (data) => request.put('/device', data)
export const deviceDelete = (id) => request.delete(`/device/${id}`)

// 报修闭环
export const repairSubmit = (data) => request.post('/repair', data)
export const repairStart = (id, remark) =>
  request.post(`/repair/${id}/start`, null, { params: { remark } })
export const repairComplete = (id, remark) =>
  request.post(`/repair/${id}/complete`, null, { params: { remark } })
export const repairCancel = (id, remark) =>
  request.post(`/repair/${id}/cancel`, null, { params: { remark } })
export const repairPage = (params) => request.get('/repair/page', { params })
export const repairMine = (params) => request.get('/repair/mine', { params })
export const repairDetail = (id) => request.get(`/repair/${id}`)
// 报修可选设备清单（精简字段，普通用户无需设备台账权限即可获取）
export const repairDeviceOptions = () => request.get('/repair/device-options')

// 预约
export const reservationSubmit = (data) => request.post('/reservation/submit', data)
export const reservationCancel = (id, reason) =>
  request.put(`/reservation/${id}/cancel`, null, { params: { reason } })
export const reservationApprove = (data) => request.post('/reservation/approve', data)
export const reservationMine = (params) => request.get('/reservation/mine', { params })
export const reservationPage = (params) => request.get('/reservation/page', { params })
export const reservationDetail = (id) => request.get(`/reservation/${id}`)
// 导出 Excel：必须走 axios 以携带 JWT，并用 blob 接收二进制流
export const reservationExport = (params) => request.get('/reservation/export', { params, responseType: 'blob' })

// 统计（ECharts 数据源）
export const statsOverview = () => request.get('/stats/overview')
export const statsHotLabs = (topN = 10) => request.get('/stats/hot-labs', { params: { topN } })
export const statsReservationStatus = () => request.get('/stats/reservation-status')
export const statsReservationTrend = (days = 7) =>
  request.get('/stats/reservation-trend', { params: { days } })
export const statsLabType = () => request.get('/stats/lab-type')

// 实验室实时占用看板
export const boardRealtime = () => request.get('/stats/realtime-board')
export const boardSummary = () => request.get('/stats/board-summary')

// 签到核销
export const checkinCode = (reservationId) => request.post(`/checkin/code/${reservationId}`)
export const checkinVerify = (data) => request.post('/checkin/verify', data)
export const checkinCheckout = (id, remark) =>
  request.post(`/checkin/${id}/checkout`, null, { params: { remark } })
export const checkinMine = (params) => request.get('/checkin/mine', { params })
export const checkinPage = (params) => request.get('/checkin/page', { params })
export const checkinDetail = (id) => request.get(`/checkin/${id}`)

// 站内消息通知中心
export const noticeMine = (params) => request.get('/notice/mine', { params })
export const noticeUnreadCount = () => request.get('/notice/unread-count')
export const noticeRead = (id) => request.put(`/notice/${id}/read`)
export const noticeReadAll = () => request.put('/notice/read-all')
export const noticeDelete = (id) => request.delete(`/notice/${id}`)
export const noticePage = (params) => request.get('/notice/page', { params })
export const noticeSend = (data) => request.post('/notice/send', data)

// 个人中心
export const profileDetail = () => request.get('/profile')
export const profileUpdate = (data) => request.put('/profile', data)
export const profileChangePwd = (data) => request.put('/profile/password', data)
export const profileStats = () => request.get('/profile/stats')

// AI 助手（流式对话在 utils/sse.js 中基于 fetch 实现，这里只放历史相关接口）
export const aiHistory = () => request.get('/ai/history')
export const aiHistoryClear = () => request.delete('/ai/history')

// 操作日志
export const operLogPage = (params) => request.get('/oper-log/page', { params })