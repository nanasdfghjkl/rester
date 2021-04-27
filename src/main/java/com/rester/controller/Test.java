package com.rester.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Test{

    @RequestMapping("test1")
    /**
     *  @ResponseBody
     */
    public String test1(){
        return "success";
    }

    @RequestMapping("test2")
    public String test2(){
        return "error";
    }
}
