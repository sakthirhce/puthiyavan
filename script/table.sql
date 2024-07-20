create table orb_config(orb_config_id varchar primary key,
capital numeric(60,2) not null,
allocated_capital numeric(60,2) not null,
margin numeric(20,0) not null,
create_timestamp timestamp not null);

commit;

create table orb_stock_data(
symbol varchar primary key,
change_percentage numeric(60,2) not null,
stock_price numeric(60,2) not null,
quantity numeric(20,0) not null);

create table orb_trade_data(stock_name varchar(100) primary key,
qty numeric(20) not null,
high_price numeric(60,2)not null,
low_price numeric(60,2)not null,
stock_id numeric(40) not null,
is_order_placed boolean not null default false,
is_sl_placed boolean not null default false,
is_exited boolean not null default false,
entry_order_id varchar(60),
sl_order_id varchar(60),
entry_type varchar(60),
amount_per_stock numeric(60,2)not null,
is_errored boolean not null default false);

create table ALGO_TEST_DATA(
data_key varchar(100) primary key,
qty numeric(20) not null,
buy_price numeric(60,2) null,
sell_price numeric(60,2) null,
user_id  varchar(40) not null,
algo_name varchar(60),
entry_type varchar(60),
strike varchar(60),
instrument varchar(100) not null,
entry_date DATE,
exit_date DATE,
trade_date DATE,
entry_time TIMESTAMP,
exit_time TIMESTAMP,
charges numeric(60,2) null,
pl_after_charges numeric(60,2) null,
profit_loss numeric(60,2) null,
Entry_day varchar(60) null);

CREATE UNIQUE INDEX idx_ALGO_TEST_DATA
ON ALGO_TEST_DATA(algo_name, entry_type,strike,instrument,entry_time);

CREATE table BANK_NIFTY_OPTION (
	exp_key VARCHAR(50)  PRIMARY KEY,
	strike VARCHAR (50),
  	option_type VARCHAR (10),
	exp_date  DATE,
        UNIQUE(strike,option_type,exp_date)
);

CREATE table INDEX (
	index_key VARCHAR(50)  PRIMARY KEY,
	index_name VARCHAR (50),
        UNIQUE(index_name)
);


CREATE table trade_user (
	user_id VARCHAR(50)  PRIMARY KEY,
	broker VARCHAR (50),
	enabled boolean,
        UNIQUE(user_id)
);

CREATE table trade_strategy (
    trade_strategy_key VARCHAR(50)  PRIMARY KEY,
	index VARCHAR(50), --NIFTY/BNF
	entry_time VARCHAR(50),
	trade_validity VARCHAR(50), --MIS,BTST
	exit_time VARCHAR(50),
	intraday_exit_time VARCHAR(50),
	trade_days VARCHAR(50),
	user_id VARCHAR(50),
	strategy_enabled boolean not null default false,
	strike_selection_type VARCHAR(50),--ATM/Price Range
	strike_price_range_low numeric(10,2), --350
	strike_price_range_high numeric(10,2), --450
	strike_closest_premium numeric(10,2), --5
	order_type VARCHAR(50), --BUY/SELL
	strike_type VARCHAR(50), --PE/CE
	entry_order_type VARCHAR(50), --market/limit
	exit_order_type VARCHAR(50), --market/limit
	simple_momentum boolean not null default false,
	simple_momentum_type VARCHAR(50), --percent/point
	simple_momentum_value numeric(10,2),
	range_break boolean not null default false,
	range_break_time VARCHAR(50),
    range_break_side VARCHAR(50), --high/low
    range_break_instrument VARCHAR(50), --index/options
    reentry boolean not null default false,
    reentry_type VARCHAR(50), --ASAP/COST
    reentry_count numeric(10,2),
    positional_lot_size numeric(10,2),
    intraday_lot_size numeric(10,2),
    reentry_count numeric(10,2),
    sl_type VARCHAR(50), --percent/point
    sl_value numeric(10,2),
    trail_sl_type VARCHAR(50), --percent/point
    trail_sl_moves numeric(10,2),
    trail_sl_move numeric(10,2),
    sl_order_type VARCHAR(50), --market/limit
    target boolean not null default false,
    target_type VARCHAR(50),--percent/point
    target_value numeric(10,2),
    target_order_type VARCHAR(50), --market/limit
    alias_name VARCHAR(50),
	CONSTRAINT user_fk
              FOREIGN KEY(user_id)
        	  REFERENCES trade_user(user_id)
);
CREATE table INDEX_DATA (
	index_key VARCHAR(50),
	trade_time timestamp,
       open numeric(10,2),
        high numeric(10,2),
       low numeric(10,2),
       close numeric(10,2),
       volume numeric(50,2),
       oi numeric(50,2),
        vwap numeric(10,2),
           CONSTRAINT fk_bn_key
              FOREIGN KEY(index_key)
        	  REFERENCES INDEX(index_key)
);
CREATE table INDEX_DAY_DATA (
	index_key VARCHAR(50),
	trade_time timestamp,
       open numeric(10,2),
        high numeric(10,2),
       low numeric(10,2),
       close numeric(10,2),
       volume numeric(50,2),
       oi numeric(50,2),
        vwap numeric(10,2),
           CONSTRAINT fk_bn_key
              FOREIGN KEY(index_key)
        	  REFERENCES INDEX(index_key)
);
CREATE table INDEX_WEEK_DATA (
	index_key VARCHAR(50),
	trade_time timestamp,
       open numeric(10,2),
        high numeric(10,2),
       low numeric(10,2),
       close numeric(10,2),
       volume numeric(50,2),
       oi numeric(50,2),
        vwap numeric(10,2),
           CONSTRAINT fk_bn_key
              FOREIGN KEY(index_key)
        	  REFERENCES INDEX(index_key)
    );
    CREATE table INDEX_MONTH_DATA (
        index_key VARCHAR(50),
        trade_time timestamp,
           open numeric(10,2),
            high numeric(10,2),
           low numeric(10,2),
           close numeric(10,2),
           volume numeric(50,2),
           oi numeric(50,2),
            vwap numeric(10,2),
               CONSTRAINT fk_bn_key
                  FOREIGN KEY(index_key)
                  REFERENCES INDEX(index_key)
    );


CREATE table INDEX_YEAR_DATA (
	index_key VARCHAR(50),
	trade_time timestamp,
       open numeric(10,2),
        high numeric(10,2),
       low numeric(10,2),
       close numeric(10,2),
       volume numeric(50,2),
       oi numeric(50,2),
        vwap numeric(10,2),
           CONSTRAINT fk_bn_key
              FOREIGN KEY(index_key)
        	  REFERENCES INDEX(index_key)
);


CREATE table INDEX_YEAR_DATA (
	index_key VARCHAR(50),
	trade_time timestamp,
       open numeric(10,2),
        high numeric(10,2),
       low numeric(10,2),
       close numeric(10,2),
       volume numeric(50,2),
       oi numeric(50,2),
        vwap numeric(10,2),
           CONSTRAINT fk_bn_key
              FOREIGN KEY(index_key)
        	  REFERENCES INDEX(index_key)
);
CREATE TABLE BANK_NIFTY_OPTION_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
   trade_time timestamp,
   open numeric(10,2),
    high numeric(10,2),
   low numeric(10,2),
   close numeric(10,2),
   volume numeric(50,2),
   oi numeric(50,2),
    vwap numeric(10,2),
    exp_key  VARCHAR(50),
   CONSTRAINT fk_bnop_key
      FOREIGN KEY(exp_key)
	  REFERENCES BANK_NIFTY_OPTION(exp_key)
);



CREATE table STOCK (
	symbol VARCHAR(50)  PRIMARY KEY,
	fyer_symbol VARCHAR(50)
);
CREATE TABLE STOCK_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(10,2),
   low numeric(10,2),
   high numeric(10,2),
   close numeric(10,2),
   volume numeric(50,2),
    vwap numeric(10,2),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES STOCK(symbol),
	  UNIQUE(symbol,trade_time)
);

CREATE TABLE STOCK_DAY_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(10,2),
   low numeric(10,2),
   high numeric(10,2),
   close numeric(10,2),
   volume numeric(50,2),
    vwap numeric(10,2),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES STOCK(symbol),
	  UNIQUE(symbol,trade_time)
);

CREATE TABLE STOCK_WEEK_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(10,2),
   low numeric(10,2),
   high numeric(10,2),
   close numeric(10,2),
   volume numeric(50,2),
    vwap numeric(10,2),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES STOCK(symbol),
	  UNIQUE(symbol,trade_time)
);

CREATE TABLE STOCK_MONTH_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(10,2),
   low numeric(10,2),
   high numeric(10,2),
   close numeric(10,2),
   volume numeric(50,2),
    vwap numeric(10,2),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES STOCK(symbol),
	  UNIQUE(symbol,trade_time)
);
CREATE TABLE STOCK_YEAR_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(10,2),
   low numeric(10,2),
   high numeric(10,2),
   close numeric(10,2),
   volume numeric(50,2),
    vwap numeric(10,2),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES STOCK(symbol),
	  UNIQUE(symbol,trade_time)
);
CREATE table Crypto_Futures (
	symbol VARCHAR(50)  PRIMARY KEY
);
CREATE TABLE Crypto_Futures_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(20,10),
     low numeric(20,10),
     high numeric(20,10),
     close numeric(20,10),
     volume numeric(100,10),
      vwap numeric(20,10),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES Crypto_Futures(symbol),
	  UNIQUE(symbol,trade_time)
);

CREATE TABLE Crypto_Futures_Day_DATA(
data_key VARCHAR(50)  PRIMARY KEY,
symbol VARCHAR(50),
   trade_time timestamp,
   open numeric(20,10),
   low numeric(20,10),
   high numeric(20,10),
   close numeric(20,10),
   volume numeric(100,10),
    vwap numeric(20,10),
   CONSTRAINT fk_symbol_key
      FOREIGN KEY(symbol)
	  REFERENCES Crypto_Futures(symbol),
	  UNIQUE(symbol,trade_time)
);


create table open_trade_data_backup(
data_key varchar(100) primary key,
stock_name varchar(100) not null,
qty numeric(20) not null,
buy_price numeric(60,2) null,
sell_price numeric(60,2) null,
sl_percentage numeric(60,2) null,
sl_price numeric(60,2) null,
stock_id numeric(40)  null,
user_id  varchar(40) not null,
status varchar(40)  null,
is_order_placed boolean not null default false,
is_sl_placed boolean not null default false,
is_exited boolean not null default false,
entry_order_id varchar(60),
sl_order_id varchar(60),
algo_name varchar(60),
entry_type varchar(60),
amount_per_stock numeric(60,2) null,
is_errored boolean not null default false);

ALTER TABLE open_trade_data_backup ADD COLUMN charges numeric(60,2) null
ALTER TABLE open_trade_data_backup ADD COLUMN pl_after_charges numeric(60,2) null;
ALTER TABLE open_trade_data_backup ADD COLUMN pl_after_charges numeric(60,2) null;
create table strangle_trade_data(
data_key varchar(100) primary key,
stock_name varchar(100) not null,
qty numeric(20) not null,
buy_price numeric(60,2) null,
sell_price numeric(60,2) null,
sl_percentage numeric(60,2) null,
sl_price numeric(60,2) null,
stock_id numeric(40)  null,
user_id  varchar(40) not null,
status varchar(40)  null,
is_order_placed boolean not null default false,
is_sl_placed boolean not null default false,
is_exited boolean not null default false,
entry_order_id varchar(60),
sl_order_id varchar(60),
algo_name varchar(60),
entry_type varchar(60),
amount_per_stock numeric(60,2) null,
is_errored boolean not null default false);

ALTER TABLE open_trade_data_backup
ADD COLUMN is_sl_cancelled boolean default false;
ALTER TABLE open_trade_data ADD COLUMN trade_strategy_key varchar(100) null;

;
ALTER TABLE trade_strategy ADD COLUMN range_type varchar(100) null;
ALTER TABLE open_trade_data_backup ADD COLUMN trade_strategy_key varchar(100) null;
  insert into public.open_trade_data(
  data_key ,
  stock_name,
  qty,
  buy_price,
  sell_price,
  sl_percentage,
  sl_price,
  stock_id,
  user_id,
  status,
  is_order_placed,
  is_sl_placed,
  is_exited,
  entry_order_id,
  sl_order_id,
  algo_name,
  entry_type,
  amount_per_stock,is_errored,
  create_timestamp,exit_order_id,isslhit,is_sl_cancelled) values('f001e62f-cce4-4fa9-8389-a1e73f0aa3e5',
  'BANKNIFTY2231035500CE',75,0,765.05,20,918.00,11030018,'RS4899','',true,false,false,'220247200561728','','STRADDLE_LONG','SELL',0,false,null,'',false,false);


  ALTER TABLE trade_strategy ADD COLUMN range_candle_interval numeric(60,2) null;
  ALTER TABLE trade_strategy ADD COLUMN multiplier numeric(60,2) null;
    ALTER TABLE trade_strategy ADD COLUMN bbs_window numeric(60,2) null;
  ALTER TABLE trade_strategy ADD COLUMN sz numeric(60,2) null;
  ALTER TABLE trade_strategy ADD COLUMN s_position_taken boolean default false;
  ALTER TABLE trade_strategy ADD COLUMN no_exit boolean default false;
    ALTER TABLE trade_strategy ADD COLUMN no_sl boolean default false;
        ALTER TABLE live_pl_change ADD COLUMN entry_type varchar(100);
        ALTER TABLE live_pl_change ADD COLUMN index varchar(100);
         ALTER TABLE live_pl_change ADD COLUMN strike_type varchar(100);
update trade_strategy set trail_enabled=false where trade_strategy_key not like '%BBS%';
update trade_strategy set multiplier=0 where trade_strategy_key not like '%BBS%';
update trade_strategy set s_position_taken=false where trade_strategy_key not like '%BBS%';
update trade_strategy set range_candle_interval=1 where trade_strategy_key not like '%BBS%';
update trade_strategy set sz=1 where trade_strategy_key not like '%BBS%';
update trade_strategy set bbs_window=1 where trade_strategy_key not like '%BBS%';

create table indicator_high_level_data(
data_key varchar(100) primary key,
stock_name varchar(100) not null,strike_id varchar(100) not null,interval numeric(60,2) null);

create table indicator_data(indicator_data_key varchar(100) primary key,
data_key varchar(100), candle_time timestamp,
                                         open numeric(10,2),
                                          high numeric(10,2),
                                         low numeric(10,2),
                                         close numeric(10,2),
                                         volume numeric(50,2),
                                         oi numeric(50,2),
                                          vwap numeric(10,2),bb_upperband numeric(50,2) ,bb_lowerband numeric(50,2),bb_sma numeric(50,2),
                                          CONSTRAINT fk_indicator_data_key
                                                FOREIGN KEY(data_key)
                                          	  REFERENCES indicator_high_level_data(data_key),
                                          	  UNIQUE(data_key,candle_time));
 create table live_pl_change(data_key varchar(100) primary key,
 trade_strategy_key varchar(100), stock_name varchar(40),
                                          pl numeric(10,2),
                                           close numeric(10,2),
                                          data_time timestamp);


ALTER TABLE trade_strategy ADD COLUMN websocket_sl_enabled boolean default false;
ALTER TABLE trade_strategy ADD COLUMN temp_sl_type varchar(100);
ALTER TABLE trade_strategy ADD COLUMN temp_sl_percentage numeric(60,2) null;
ALTER TABLE trade_strategy ADD COLUMN hedge boolean default false;

ALTER TABLE open_trade_data ADD COLUMN websocket_sl_modified boolean default false;
ALTER TABLE open_trade_data ADD COLUMN websocket_sl_time varchar(100);
ALTER TABLE open_trade_data ADD COLUMN temp_sl_price numeric(60,2) null;

insert into user_subscription(user_subscription_key,lot_size,trade_strategy_key,user_id) values('ss-fri-atm-09:35-buy-LTK728',1,'ss-fri-atm-09:35-buy','LTK728');
insert into user_subscription(user_subscription_key,lot_size,trade_strategy_key,user_id) values('ss-fri-atm-09:35-buy-YC0209',1,'ss-fri-atm-09:35-buy','YC0209');
insert into user_subscription(user_subscription_key,lot_size,trade_strategy_key,user_id) values('ss-fri-atm-09:35-buy-AAJ686',1,'ss-fri-atm-09:35-buy','AAJ686');

Tick