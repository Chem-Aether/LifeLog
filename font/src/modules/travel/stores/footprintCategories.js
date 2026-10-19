const categories = [
  ['旅游景点', '自然景观', 'scenic_nature', 'mountain', '#16A765'],
  ['旅游景点', '公园与园林', 'scenic_park', 'park', '#42B83D'],
  ['旅游景点', '历史遗址与古建筑', 'scenic_heritage', 'heritage', '#B96A25'],
  ['旅游景点', '古镇与历史街区', 'scenic_old_town', 'old_town', '#A55731'],
  ['旅游景点', '博物馆与展馆', 'scenic_museum', 'museum', '#8050D8'],
  ['旅游景点', '宗教与礼制建筑', 'scenic_religion', 'temple', '#CF3E9A'],
  ['旅游景点', '地标与特色建筑', 'scenic_landmark', 'landmark', '#E3A600'],
  ['旅游景点', '观景与拍照点', 'scenic_viewpoint', 'viewpoint', '#F07818'],
  ['旅游景点', '主题乐园与动物园', 'scenic_theme_park', 'theme_park', '#EB3D62'],
  ['旅游景点', '红色与纪念地', 'scenic_memorial', 'memorial', '#D92D20'],
  ['旅游景点', '户外与徒步线路', 'scenic_outdoor', 'hiking', '#00A896'],
  ['旅游景点', '特色体验', 'scenic_experience', 'experience', '#E35D36'],
  ['餐饮美食', '中式正餐', 'food_chinese', 'restaurant_chinese', '#E33B2E'],
  ['餐饮美食', '异国料理', 'food_international', 'restaurant_international', '#F0523A'],
  ['餐饮美食', '快餐与小吃', 'food_quick_bite', 'fast_food', '#F29216'],
  ['餐饮美食', '咖啡馆', 'food_cafe', 'cafe', '#8C4B2D'],
  ['餐饮美食', '茶饮与甜品', 'food_drink_dessert', 'tea_drink', '#D9469D'],
  ['餐饮美食', '其他餐饮', 'food_other', 'restaurant', '#CB4035'],
  ['住宿', '酒店', 'stay_hotel', 'hotel', '#405FE8'],
  ['住宿', '民宿与客栈', 'stay_guesthouse', 'guesthouse', '#6558E8'],
  ['住宿', '旅舍与度假住宿', 'stay_hostel_resort', 'hostel', '#8A63E8'],
  ['交通出行', '铁路车站', 'transit_rail', 'train', '#087F8C'],
  ['交通出行', '地铁与城市轨道', 'transit_urban_rail', 'metro', '#087DB5'],
  ['交通出行', '机场', 'transit_airport', 'plane', '#326CE0'],
  ['交通出行', '公交与公路客运', 'transit_bus', 'bus', '#138B65'],
  ['交通出行', '港口与轮渡', 'transit_port', 'ship', '#0786A8'],
  ['交通出行', '停车与充电', 'transit_parking', 'parking', '#596B78'],
  ['交通出行', '公路服务设施', 'transit_road_service', 'road_service', '#7A9E18'],
  ['购物消费', '商场与百货', 'shopping_mall', 'mall', '#D53683'],
  ['购物消费', '超市与便利店', 'shopping_grocery', 'store', '#E34C99'],
  ['购物消费', '商业街与市场', 'shopping_street_market', 'market', '#E64763'],
  ['购物消费', '专卖与特色零售', 'shopping_specialty', 'shopping_bag', '#BB378D'],
  ['文化休闲', '影院与剧场', 'culture_cinema', 'cinema', '#7040D6'],
  ['文化休闲', '演出与活动场馆', 'culture_venue', 'performance', '#9144C7'],
  ['文化休闲', '夜间娱乐', 'leisure_nightlife', 'nightlife', '#A236C0'],
  ['文化休闲', '运动与健身', 'leisure_sports', 'sports', '#15964D'],
  ['文化休闲', '日常休闲娱乐', 'leisure_daily', 'leisure', '#D68514'],
  ['教育文化', '学校与培训', 'education_school', 'school', '#2879C7'],
  ['教育文化', '图书馆与文化馆', 'education_library', 'library', '#4269D8'],
  ['教育文化', '科研与科普机构', 'education_research', 'research', '#3156B7'],
  ['医疗健康', '医疗机构', 'healthcare_medical', 'medical', '#D93851'],
  ['医疗健康', '药店', 'healthcare_pharmacy', 'pharmacy', '#EB4D5C'],
  ['公共服务', '政府与办事服务', 'public_government', 'government', '#536E91'],
  ['公共服务', '金融服务', 'public_finance', 'bank', '#16877C'],
  ['公共服务', '公共设施', 'public_facility', 'facility', '#687D8D'],
  ['生活地点', '居住与社区', 'life_residential', 'home', '#A45E3E'],
  ['生活地点', '生活服务', 'life_daily_service', 'service', '#89951B'],
  ['工作商务', '办公与商务园区', 'work_office', 'office', '#26718A'],
  ['工作商务', '工厂与生产基地', 'work_factory', 'factory', '#658B29'],
  ['工作商务', '农林牧渔基地', 'work_agriculture', 'agriculture', '#4C982A'],
  ['其他', '其他地点', 'other_place', 'pin', '#647480'],
].map(([group, label, value, icon, color]) => ({
  group,
  label,
  value,
  icon,
  color,
  iconUrl: `/travel/icons/${icon}.svg`,
}))

const groups = new Map()
for (const category of categories) {
  if (!groups.has(category.group)) groups.set(category.group, [])
  groups.get(category.group).push(category)
}

export const footprintCategoryOptions = categories
export const footprintCategoryGroups = [...groups].map(([label, options]) => ({ label, options }))

export function getFootprintCategory(value) {
  return categories.find(category => category.value === value) || null
}

export function getFootprintCategoryLabel(value) {
  return getFootprintCategory(value)?.label || '其他地点'
}

export function getFootprintCategoryIcon(value) {
  return getFootprintCategory(value) || categories.at(-1)
}

export function normalizeFootprintCategory(value) {
  return getFootprintCategory(value)?.value || categories.at(-1).value
}
