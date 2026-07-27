-- プラグイン起動時に Schema.kt が同じ内容を自動で適用するため、
-- 手動で流す必要はない。参照用に残している。

create table if not exists item_list
(
	id int auto_increment,
	item_name varchar(128) null,
	item_type varchar(64) null,
	base64 longtext null,
	constraint item_list_pk
		primary key (id)
);

create table if not exists order_table
(
	id int auto_increment,
	player varchar(16) null,
	uuid varchar(36) null,
	item_id int not null,
	item_name varchar(128) null,
	date DATETIME null,
	amount int null,
	price double not null,
	is_op tinyint not null default 0,
	expired tinyint not null default 0,
	constraint order_table_pk
		primary key (id)
);

-- 出品者検索・出品数カウント用
create index order_table_uuid_item_id_index
	on order_table (uuid, item_id, is_op, expired);

-- アイテムごとの最安値抽出と、同一アイテムの一覧表示用
create index idx_order_active_item_price
	on order_table (expired, item_id, price, id);

-- Amanzon Basic(公式出品)の一覧用
create index idx_order_official
	on order_table (is_op, expired, price);

-- 1週間経過した出品の一括取り下げ用
create index idx_order_expire_sweep
	on order_table (expired, is_op, date);

create table if not exists log
(
	id int auto_increment,
	order_player varchar(16) null,
	target_player varchar(16) null,
	action varchar(16) null,
	item_id int null,
	item_name varchar(128) null,
	amount int null,
	price double null,
	date datetime null,
	constraint log_pk
		primary key (id)
);
