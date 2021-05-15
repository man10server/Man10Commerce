create table item_list
(
	id int auto_increment,
	item_name varchar(128) null,
	item_type varchar(64) null,
	base64 longtext null,
	constraint item_list_pk
		primary key (id)
);

create table order_table
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
	constraint order_table_pk
		primary key (id)
);

create index order_table_uuid_item_id_index
	on order_table (uuid, item_id, is_op);

create table log
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

create table prime_list
(
	id int auto_increment,
	player varchar(16) null,
	uuid varchar(16) null,
	pay_date DATETIME null,
	constraint prime_list_pk
		primary key (id)
);

create index prime_list_uuid_pay_date_index
	on prime_list (uuid, pay_date);

