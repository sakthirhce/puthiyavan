package com.sakthi.trade.options.buy.banknifty;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.sakthi.trade.domain.TradeReport;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class TestList {

    public static void main(String[] args) throws FileNotFoundException {
        List<Integer> integerList=new ArrayList<>();
        integerList.add(1);
        integerList.add(2);
        List<Integer> integerList1=integerList.subList(0,5);

    }
}
