package com.rester.controller;

import io.swagger.handler.ConfigManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class CategoryController {

    @RequestMapping("/empirical-result")
    @CrossOrigin(origins = "*", maxAge = 3600)
    public Map test2(String category){
        System.out.println(category);
        String categoryResult[]=null;
        if(category!=null){

            categoryResult= ConfigManager.getInstance().getValue(category.toUpperCase()).split(",",-1);
            System.out.println(categoryResult);
        }
        Map<String,Object> result=new HashMap<>();
        result.put("categoryResult",categoryResult);
        return result;
    }
}
