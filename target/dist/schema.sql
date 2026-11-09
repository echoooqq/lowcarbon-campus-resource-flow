-- ======================================================================
-- 低碳校园资源循环流转桌面系统 — SQLite 数据库初始化脚本
-- 数据库文件：lowcarbon.db（Jar 同级目录 / 项目根目录）
-- 版本：v1.0 | 最后更新：2026-11-08
-- 执行方式：程序启动时由 DatabaseManager 自动执行
-- ======================================================================

-- 开启外键检查（SQLite 默认关闭，每次获取 Connection 后均需设置）
PRAGMA foreign_keys = ON;

-- ==================== user — 用户账号表 ====================
CREATE TABLE IF NOT EXISTS user (
    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    salt          VARCHAR(32)  NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
    credit_score  INTEGER      NOT NULL DEFAULT 100,
    create_time   INTEGER      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_username ON user(username);
CREATE INDEX IF NOT EXISTS idx_user_role    ON user(role);

-- ==================== category — 物品分类表 ====================
CREATE TABLE IF NOT EXISTS category (
    category_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(200)
);

-- ==================== item — 闲置物品表 ====================
CREATE TABLE IF NOT EXISTS item (
    item_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    publisher_id INTEGER      NOT NULL,
    name         VARCHAR(100) NOT NULL,
    category_id  INTEGER      NOT NULL,
    description  TEXT,
    price        REAL         NOT NULL DEFAULT 0.0,
    trade_type   VARCHAR(10)  NOT NULL DEFAULT 'SELL',
    status       VARCHAR(15)  NOT NULL DEFAULT 'ON_SALE',
    tags         VARCHAR(200),
    publish_time INTEGER      NOT NULL,
    FOREIGN KEY (publisher_id) REFERENCES user(user_id),
    FOREIGN KEY (category_id)  REFERENCES category(category_id)
);

CREATE INDEX IF NOT EXISTS idx_item_publisher ON item(publisher_id);
CREATE INDEX IF NOT EXISTS idx_item_category  ON item(category_id);
CREATE INDEX IF NOT EXISTS idx_item_status    ON item(status);
CREATE INDEX IF NOT EXISTS idx_item_name      ON item(name);

-- ==================== transaction_record — 流转记录表 ====================
CREATE TABLE IF NOT EXISTS transaction_record (
    record_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id       INTEGER     NOT NULL,
    initiator_id  INTEGER     NOT NULL,
    status        VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    create_time   INTEGER     NOT NULL,
    complete_time INTEGER,
    remark        VARCHAR(500),
    FOREIGN KEY (item_id)      REFERENCES item(item_id),
    FOREIGN KEY (initiator_id) REFERENCES user(user_id)
);

CREATE INDEX IF NOT EXISTS idx_trans_item      ON transaction_record(item_id);
CREATE INDEX IF NOT EXISTS idx_trans_initiator ON transaction_record(initiator_id);
CREATE INDEX IF NOT EXISTS idx_trans_status    ON transaction_record(status);

-- ==================== review — 评价表 ====================
CREATE TABLE IF NOT EXISTS review (
    review_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id    INTEGER     NOT NULL,
    reviewer_id  INTEGER     NOT NULL,
    reviewee_id  INTEGER     NOT NULL,
    rating       INTEGER     NOT NULL CHECK(rating BETWEEN 1 AND 5),
    comment      VARCHAR(500),
    create_time  INTEGER     NOT NULL,
    FOREIGN KEY (record_id)   REFERENCES transaction_record(record_id),
    FOREIGN KEY (reviewer_id) REFERENCES user(user_id),
    FOREIGN KEY (reviewee_id) REFERENCES user(user_id)
);

CREATE INDEX IF NOT EXISTS idx_review_record   ON review(record_id);
CREATE INDEX IF NOT EXISTS idx_review_reviewer ON review(reviewer_id);
CREATE INDEX IF NOT EXISTS idx_review_reviewee ON review(reviewee_id);

-- ======================================================================
-- 初始化数据（使用 INSERT OR IGNORE 保证脚本可重复执行）
-- ======================================================================

-- 预置物品分类
INSERT OR IGNORE INTO category (name, description) VALUES ('教材',     '各类课程教材、参考书籍');
INSERT OR IGNORE INTO category (name, description) VALUES ('数码配件', '耳机、充电器、U盘、键盘鼠标等');
INSERT OR IGNORE INTO category (name, description) VALUES ('文体用品', '运动器材、乐器、画材等');
INSERT OR IGNORE INTO category (name, description) VALUES ('生活物资', '收纳用品、小家电、床上用品等');
