/*
package com.sakthi.trade.zerodha.instrument;

import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.zerodha.ZerodhaTransactionService;
import com.sakthi.trade.zerodha.entity.Instrument;
import com.sakthi.trade.zerodha.jpa.InstrumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class InstrumentService {
    @Autowired
    ZerodhaTransactionService transactionService;



    @Autowired
    InstrumentRepository instrumentRepository;

    private List<Instrument> instrumentList = new ArrayList<>();


    public List<Instrument> saveAllInstrument() throws IOException, CsvValidationException {
    transactionService.getInstrument();

        Instrument instrument = new Instrument();

        instrumentList.add(instrument);

        return instrumentList;
    }

}
*/
