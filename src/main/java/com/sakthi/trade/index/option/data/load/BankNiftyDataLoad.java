package com.sakthi.trade.index.option.data.load;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvValidationException;
import com.sakthi.trade.entity.BankNiftyOptionDataEntity;
import com.sakthi.trade.entity.BankNiftyOptionEntity;
import com.sakthi.trade.repo.BankNiftyOptionDataRepository;
import com.sakthi.trade.repo.BankNiftyOptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
public class BankNiftyDataLoad {

    @Autowired
    BankNiftyOptionRepository bankNiftyOptionRepository;
    @Autowired
    BankNiftyOptionDataRepository bankNiftyOptionDataRepository;
    ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
    public void loadData() throws IOException, CsvException, ParseException {
        String maindirpath = "/home/hasvanth/Downloads/findata/BANKNIFTY/options";

        // File object
        File maindir = new File(maindirpath);

        if (maindir.exists() && maindir.isDirectory()) {
            File expiry[] = maindir.listFiles();
            for (int i = 0; i < expiry.length; i++) {
                int finali=i;
                if (expiry[i].isDirectory()) {
                    System.out.println(new Date()+":"+expiry[i].getName());
                    File strike[] = expiry[i].listFiles();
                    try {
                        for (int j = 0; j < strike.length; j++) {
                            int finalj=j;
                            executorService.submit(() -> {
                            if (strike[finalj].isFile()){
                                System.out.println(new Date()+":"+strike[finalj].getName());

                                FileReader filereader = null;
                                try {
                                    filereader = new FileReader(strike[finalj]);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }
                                CSVReader csvReader = new CSVReaderBuilder(filereader)
                                    .withSkipLines(1)
                                    .build();
                                List<String[]> allData = null;
                                try {
                                    allData = csvReader.readAll();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (CsvException e) {
                                    e.printStackTrace();
                                }

                                List<BankNiftyOptionDataEntity> bankNiftyOptionDataEntityList = new ArrayList<>();
                            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm:ss");
                            SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yy HH:mm");
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                            SimpleDateFormat dateFormat1 = new SimpleDateFormat("dd-MM-yyyy");
                            if (allData.size() > 2) {
                                BankNiftyOptionEntity optionEntity = new BankNiftyOptionEntity();
                                if (strike[finalj].getName().contains("CE")) {
                                    optionEntity.setOptionType("CE");
                                } else {
                                    optionEntity.setOptionType("PE");
                                }
                                try {
                                    optionEntity.setExpDate(dateFormat1.parse(dateFormat1.format(dateFormat.parse(expiry[finali].getName()))));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                                //   String[] strikeName = strike[j].getName().split(".");
                                String expKey = UUID.randomUUID().toString();
                                optionEntity.setExpKey(expKey);
                                optionEntity.setStrike(strike[finalj].getName().substring(0,strike[finalj].getName().length()-4));
                                bankNiftyOptionRepository.save(optionEntity);
                                bankNiftyOptionRepository.flush();
                                int k=0;
                                for (String[] lineS : allData) {

                                        k++;
                                    BankNiftyOptionDataEntity bankNiftyOptionDataEntity = new BankNiftyOptionDataEntity();
                                    bankNiftyOptionDataEntity.setExpKey(expKey);
                                    Date tradeTime = null;
                                    try {
                                        tradeTime = sdf.parse(lineS[0]);
                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        bankNiftyOptionDataEntity.setTradeTime(sdf1.parse(sdf1.format(tradeTime)));
                                    } catch (ParseException e) {
                                        e.printStackTrace();
                                    }
                                    bankNiftyOptionDataEntity.setOpen(Double.valueOf(lineS[1]));
                                    bankNiftyOptionDataEntity.setHigh(Double.valueOf(lineS[2]));
                                    bankNiftyOptionDataEntity.setLow(Double.valueOf(lineS[3]));
                                    bankNiftyOptionDataEntity.setClose(Double.valueOf(lineS[4]));
                                    bankNiftyOptionDataEntity.setVolume(Integer.valueOf(lineS[5]));
                                    bankNiftyOptionDataEntity.setOi(Integer.valueOf(lineS[6]));
                                    bankNiftyOptionDataEntity.setOptionDataKey(UUID.randomUUID().toString());

                                    if (lineS.length > 7 && lineS[7] != null && lineS[7].length() > 0 && lineS[7] != "") {
                                        bankNiftyOptionDataEntity.setVwap(Double.valueOf(lineS[7]));
                                    }
                                    bankNiftyOptionDataEntityList.add(bankNiftyOptionDataEntity);
                                }
                                bankNiftyOptionDataRepository.saveAll(bankNiftyOptionDataEntityList);
                                    bankNiftyOptionDataRepository.flush();
                            }
                        }});
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public void loadDataLatest() throws IOException, CsvException, ParseException {
        String maindirpath = "/home/hasvanth/Downloads/findata/BANKNIFTY/options/Latest";

        // File object
        File maindir = new File(maindirpath);

        if (maindir.exists() && maindir.isDirectory()) {
            File expiry[] = maindir.listFiles();
            for (int i = 0; i < expiry.length; i++) {
                int finali=i;
                if (expiry[i].isDirectory()) {
                    System.out.println(new Date()+":"+expiry[i].getName());
                    File strike[] = expiry[i].listFiles();
                    try {
                        for (int j = 0; j < strike.length; j++) {
                            int finalj=j;
                         //   executorService.submit(() -> {
                                if (strike[finalj].isFile()){
                                    System.out.println(new Date()+":"+strike[finalj].getName());

                                    FileReader filereader = null;
                                    try {
                                        filereader = new FileReader(strike[finalj]);
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                    CSVReader csvReader = new CSVReaderBuilder(filereader)
                                            .withSkipLines(1)
                                            .build();
                                    List<String[]> allData = null;
                                    try {
                                        allData = csvReader.readAll();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    } catch (CsvException e) {
                                        e.printStackTrace();
                                    }

                                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm:ss");
                                    SimpleDateFormat sdf1 = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                    SimpleDateFormat dateFormat1 = new SimpleDateFormat("dd-MM-yyyy");
                                    Map<String,String> strikeNameList=new HashMap<>();
                                    if (allData.size() > 1) {
                                        int k=0;
                                        for (String[] lineS : allData) {
                                            k++;
                                            System.out.println(k);
                                            List<BankNiftyOptionDataEntity> bankNiftyOptionDataEntityList = new ArrayList<>();
                                       //     executorService.submit(() -> {
                                            if((lineS[0].contains("CE")||lineS[0].contains("CE")) && lineS[0].contains("BANKNIFTY")) {
                                                    String strikeName = lineS[0].substring(11);
                                                if (strikeNameList.get(strikeName) ==null) {

                                                    BankNiftyOptionEntity optionEntity = new BankNiftyOptionEntity();
                                                    //   String[] strikeName = strike[j].getName().split(".");
                                                    String expKey = UUID.randomUUID().toString();
                                                    optionEntity.setExpKey(expKey);
                                                    if (lineS[0].contains("CE")) {
                                                        optionEntity.setOptionType("CE");
                                                    } else {
                                                        optionEntity.setOptionType("PE");
                                                    }
                                                    try {
                                                        optionEntity.setExpDate(dateFormat1.parse(dateFormat1.format(dateFormat.parse(expiry[finali].getName()))));
                                                    } catch (ParseException e) {
                                                        e.printStackTrace();
                                                    }
                                                    optionEntity.setStrike(strikeName);
                                                    bankNiftyOptionRepository.save(optionEntity);
                                                    bankNiftyOptionRepository.flush();
                                                    strikeNameList.put(strikeName,expKey);
                                                    }


                                                if(strikeNameList.get(strikeName)!=null) {
                                                    BankNiftyOptionDataEntity bankNiftyOptionDataEntity = new BankNiftyOptionDataEntity();
                                                    bankNiftyOptionDataEntity.setExpKey(strikeNameList.get(strikeName));
                                                    Date tradeTime = null;
                                                    try {
                                                        tradeTime = sdf1.parse(lineS[1]);
                                                    } catch (ParseException e) {
                                                        e.printStackTrace();
                                                    }
                                                    try {
                                                        bankNiftyOptionDataEntity.setTradeTime(tradeTime);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    bankNiftyOptionDataEntity.setOpen(Double.valueOf(lineS[2]));
                                                    bankNiftyOptionDataEntity.setHigh(Double.valueOf(lineS[3]));
                                                    bankNiftyOptionDataEntity.setLow(Double.valueOf(lineS[4]));
                                                    bankNiftyOptionDataEntity.setClose(Double.valueOf(lineS[5]));
                                                    bankNiftyOptionDataEntity.setVolume(Integer.valueOf(lineS[6]));
                                                    bankNiftyOptionDataEntity.setOi(Integer.valueOf(lineS[7]));
                                                    bankNiftyOptionDataEntity.setOptionDataKey(UUID.randomUUID().toString());

                                                  /*  if (lineS.length > 7 && lineS[7] != null && lineS[7].length() > 0 && lineS[7] != "") {
                                                        bankNiftyOptionDataEntity.setVwap(Double.valueOf(lineS[7]));
                                                    }*/
                                                 //   bankNiftyOptionDataEntityList.add(bankNiftyOptionDataEntity);
                                                    bankNiftyOptionDataRepository.save(bankNiftyOptionDataEntity);
                                                    bankNiftyOptionDataRepository.flush();
                                                }
                                                }

                                       //     });

                                            }

                                    }}
                            //    });
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
