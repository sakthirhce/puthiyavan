package com.sakthi.trade.util;

import com.sakthi.trade.telegram.TelegramClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZippingDirectory
{
    static final int BUFFER = 1024;
    // Source folder which has to be zipped
    static final String FOLDER = "/home/ubuntu/Downloads/NIFTY/2022/Nov/2022-11-17";
    List<File> fileList = new ArrayList<File>();

    @Autowired
    TelegramClient telegramClient;
    public void test(String folderName,String zipFileName)
    {
        ZippingDirectory zf = new ZippingDirectory();
        // get list of files
        List<File> fileList = zf.getFileList(new File(folderName));
        //go through the list of files and zip them
        zf.zipFiles(fileList,zipFileName,folderName);
    }

    private void zipFiles(List<File> fileList,String fileName,String folderName)
    {
        try
        {
            // Creating ZipOutputStream - Using input name to create output name
            FileOutputStream fos = new FileOutputStream(folderName+"/"+fileName.concat(".zip"));
            ZipOutputStream zos = new ZipOutputStream(fos);
            // looping through all the files
            for(File file : fileList)
            {
                // To handle empty directory
                if(file.isDirectory())
                {
                    // ZipEntry --- Here file name can be created using the source file
                    ZipEntry ze = new ZipEntry(getFileName(file.toString())+"/");
                    // Putting zipentry in zipoutputstream
                    zos.putNextEntry(ze);
                    zos.closeEntry();
                }
                else
                {
                    FileInputStream fis = new FileInputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(fis, BUFFER);
                    // ZipEntry --- Here file name can be created using the source file
                    ZipEntry ze = new ZipEntry(getFileName(file.toString()));
                    // Putting zipentry in zipoutputstream
                    zos.putNextEntry(ze);
                    byte data[] = new byte[BUFFER];
                    int count;
                    while((count = bis.read(data, 0, BUFFER)) != -1)
                    {
                        zos.write(data, 0, count);
                    }
                    bis.close();
                    zos.closeEntry();
                }
            }
            zos.close();
          //  telegramClient.sendDocument("-713214125","Hello",fos.toString());
        }
        catch(IOException ioExp)
        {
            System.out.println("Error while zipping " + ioExp.getMessage());
            ioExp.printStackTrace();
        }
    }

    //the method returns a list of files
    private List<File> getFileList(File source)
    {
        if(source.isFile())
        {
            fileList.add(source);
        }
        else if(source.isDirectory())
        {
            String[] subList = source.list();
            // this condition checks for empty directory
            if(subList.length == 0)
            {
                //System.out.println("path -- " + source.getAbsolutePath());
                fileList.add(new File(source.getAbsolutePath()));
            }
            for(String child : subList)
            {
                getFileList(new File(source, child));
            }
        }
        return fileList;
    }

    private String getFileName(String filePath)
    {
        String name = filePath.substring(FOLDER.length() + 1, filePath.length());
        //System.out.println(" name " + name);
        return name;
    }
}  