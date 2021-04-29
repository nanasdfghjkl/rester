package com.rester.controller;

import com.google.common.primitives.Bytes;
import io.swagger.handler.ConfigManager;
import io.swagger.handler.ValidatorController;
import io.swagger.oas.inflector.models.RequestContext;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
public class ValidateController {
    @PostMapping(path ="/restapi-text")
    public Map valiByContext(@RequestParam("category") String category,@RequestParam("context") String context){
        System.out.println(category);
        ValidatorController validator = new ValidatorController();
        String categoryResult[]=null;
        if(category!=null){
            categoryResult= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);
            System.out.println(categoryResult);
        }

        Map<String, Object> result=null;
        if(context!=null){
            validator.validateByString(new RequestContext(), context);
            result=validator.getValidateResult();
        }
        result.put("categoryResult",categoryResult);
        return result;

    }

    @PostMapping(path ="/restapi-file")
    public Map valiByFile(String category, @RequestParam("testfile") MultipartFile file)  {
        System.out.println("start file validate");
        InputStream in = null;
        List<Byte> b = new ArrayList<Byte>();
        int len = 0;
        int temp = 0;          //所有读取的内容都使用temp接收
        try {
            in = file.getInputStream();
            //byte b[] = new byte[10240];
            while ((temp = in.read()) != -1) {    //当没有读取完时，继续读取
                //b[len]=(byte)temp;
                b.add((byte) temp);
                len++;
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bb = Bytes.toArray(b);
        String context = new String(bb, 0, len);

        //System.out.println(context);
        ValidatorController validator = new ValidatorController();
        String categoryResult[]=null;
        if(category!=null){
            categoryResult= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);
            System.out.println("cateresult:"+categoryResult);
        }

        Map<String, Object> result=null;
        if(context!=null){
            System.out.println("start RAD validate");
            validator.validateByString(new RequestContext(), context);
            result=validator.getValidateResult();
        }
        System.out.println("name:"+result.get("name"));
        result.put("categoryResult",categoryResult);
        return result;

    }
    @PostMapping(path ="/restapi-url")
    public Map valiByUrl(String category, String url) {
        ValidatorController validator = new ValidatorController();
        String context = null; //获取url提供的swagger文档，返回响应entity
        try {
            context = validator.getUrlContents(url, false, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println(context);

        String categoryResult[]=null;
        if(category!=null){
            categoryResult= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);
            System.out.println(categoryResult);
        }

        Map<String, Object> result=null;
        if(context!=null){
            System.out.println("start url evaluate");
            validator.validateByString(new RequestContext(), context);
            result=validator.getValidateResult();
            System.out.println("name:"+result.get("name"));
        }
        result.put("categoryResult",categoryResult);
        return result;

    }
}
