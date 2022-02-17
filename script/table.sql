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


create table open_trade_data(
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

