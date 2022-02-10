CREATE TABLE Instrument (
  trading_symbol VARCHAR(40)  PRIMARY KEY,
  instrument_token VARCHAR(40) NOT NULL,
  exchange_token VARCHAR(40) NOT NULL,
  expiry VARCHAR(40) NOT NULL,
  strike NUMERIC(40,2) NOT NULL,
  lot_size NUMERIC(20) NOT NULL,
   instrument_type VARCHAR(20) NOT NULL,
   exchange VARCHAR(20) NOT NULL
);