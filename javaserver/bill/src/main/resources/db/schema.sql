CREATE TABLE IF NOT EXISTS bill_account (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(40),
    opening_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bill_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES bill_category(id) ON DELETE RESTRICT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    icon TEXT NOT NULL DEFAULT '🏷️',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_category_root ON bill_category(name) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_category_child ON bill_category(parent_id, name) WHERE parent_id IS NOT NULL;

INSERT INTO bill_category(name, sort_order) VALUES
    ('收入',1),('餐饮',2),('交通',3),('医疗',4),('生活成本',5),('教育',6),('办公',7),('娱乐',8),('服饰',9),('个人护理',10),('购物',11),('互转',12),('其他',13)
ON CONFLICT DO NOTHING;

UPDATE bill_category AS category SET icon = icon_map.icon
FROM (VALUES
    ('收入','💰'),('餐饮','🍽️'),('交通','🚆'),('医疗','🏥'),('生活成本','🏠'),
    ('教育','🎓'),('办公','💻'),('娱乐','🎬'),('服饰','👕'),('个人护理','🧴'),
    ('购物','🛒'),('互转','🔁'),('其他','⭐')
) AS icon_map(name, icon)
WHERE category.parent_id IS NULL AND category.name = icon_map.name
  AND (category.icon IN ('🏷️','') OR category.icon LIKE 'svg:%');

INSERT INTO bill_category(name, parent_id, sort_order)
SELECT v.name,p.id,v.sort_order FROM (VALUES
('生活费','收入',1),('补助','收入',2),('工资','收入',3),('奖金','收入',4),('金融','收入',5),('退款','收入',6),('AA收入','收入',7),('出售物品','收入',8),('收还款','收入',9),
('三餐','餐饮',1),('水果','餐饮',2),('蔬菜','餐饮',3),('甜品','餐饮',4),('饮品','餐饮',5),('零食','餐饮',6),('调味料','餐饮',7),('肉蛋奶','餐饮',8),('加工食品','餐饮',9),
('单车','交通',1),('公交/地铁','交通',2),('出租车','交通',3),('铁路','交通',4),('航空','交通',5),('快递运输','交通',6),('船舶','交通',7),
('药品','医疗',1),('就诊','医疗',2),('体检','医疗',3),('医疗器械','医疗',4),('保健品','医疗',5),
('水电气','生活成本',1),('网费','生活成本',2),('话费','生活成本',3),('物业费','生活成本',4),('房租/贷','生活成本',5),
('书本','教育',1),('学费','教育',2),('文具','教育',3),('报名费','教育',4),('课程/培训','教育',5),
('正版化','办公',1),('技术服务','办公',2),('办公用品','办公',3),('数字消费','办公',4),
('影视演出','娱乐',1),('游戏','娱乐',2),('门票','娱乐',3),('娱乐社交','娱乐',4),('会员服务','娱乐',5),('场馆费','娱乐',6),('玩具','娱乐',7),
('衣服','服饰',1),('裤子','服饰',2),('鞋子','服饰',3),('内衣','服饰',4),('袜子','服饰',5),('帽子','服饰',6),
('美发','个人护理',1),('健身','个人护理',2),('美妆','个人护理',3),('按摩疗养','个人护理',4),('护肤品','个人护理',5),('护理仪器','个人护理',6),('滋补养生','个人护理',7),
('饰品','购物',1),('日用品','购物',2),('个人护理','购物',3),('居家','购物',4),('电器','购物',5),('3C数码','购物',6),('零配件','购物',7)
) AS v(name,parent_name,sort_order)
JOIN bill_category p ON p.name=v.parent_name AND p.parent_id IS NULL
ON CONFLICT DO NOTHING;

UPDATE bill_category AS category SET icon = icon_map.icon
FROM (VALUES
    ('生活费','💵'),('补助','🧧'),('工资','💼'),('奖金','🏆'),('金融','💹'),('退款','↩️'),
    ('AA收入','🤝'),('出售物品','📦'),('收还款','💳'),('三餐','🍚'),('水果','🍎'),('蔬菜','🥬'),
    ('甜品','🍰'),('饮品','🥤'),('零食','🍿'),('调味料','🧂'),('肉蛋奶','🥩'),('加工食品','🥫'),
    ('单车','🚲'),('公交/地铁','🚇'),('出租车','🚕'),('铁路','🚄'),('航空','✈️'),('快递运输','📦'),
    ('船舶','🚢'),('药品','💊'),('就诊','🩺'),('体检','🩻'),('医疗器械','🩹'),('保健品','🌿'),
    ('水电气','💡'),('网费','📶'),('话费','📱'),('物业费','🏢'),('房租/贷','🏠'),('书本','📚'),
    ('学费','🎓'),('文具','✏️'),('报名费','📝'),('课程/培训','🧑‍🏫'),('正版化','©️'),('技术服务','🛠️'),
    ('办公用品','🗂️'),('数字消费','💻'),('影视演出','🎥'),('游戏','🎮'),('门票','🎫'),('娱乐社交','🎤'),
    ('会员服务','💎'),('场馆费','🏟️'),('玩具','🧸'),('衣服','👕'),('裤子','👖'),('鞋子','👟'),
    ('内衣','🩲'),('袜子','🧦'),('帽子','🧢'),('美发','💇'),('健身','🏋️'),('美妆','💄'),
    ('按摩疗养','💆'),('护肤品','🧴'),('护理仪器','✨'),('滋补养生','🍵'),('饰品','💍'),('日用品','🧻'),
    ('个人护理','🧴'),('居家','🛋️'),('电器','🔌'),('3C数码','📱'),('零配件','🔩')
) AS icon_map(name, icon)
WHERE category.parent_id IS NOT NULL AND category.name = icon_map.name
  AND (category.icon IN ('🏷️','') OR category.icon LIKE 'svg:%');

CREATE TABLE IF NOT EXISTS bill_transaction (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    from_account_id BIGINT REFERENCES bill_account(id) ON DELETE RESTRICT,
    to_account_id BIGINT REFERENCES bill_account(id) ON DELETE RESTRICT,
    counterparty VARCHAR(200) NOT NULL DEFAULT '',
    description VARCHAR(500) NOT NULL DEFAULT '',
    category_id BIGINT REFERENCES bill_category(id) ON DELETE RESTRICT,
    remark TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_bill_flow CHECK ((from_account_id IS NOT NULL OR to_account_id IS NOT NULL) AND (from_account_id IS NULL OR to_account_id IS NULL OR from_account_id <> to_account_id))
);
CREATE INDEX IF NOT EXISTS idx_bill_transaction_occurred_at ON bill_transaction(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_bill_transaction_from_account ON bill_transaction(from_account_id);
CREATE INDEX IF NOT EXISTS idx_bill_transaction_to_account ON bill_transaction(to_account_id);
CREATE INDEX IF NOT EXISTS idx_bill_transaction_category ON bill_transaction(category_id);
