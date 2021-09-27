package io.swagger.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 动态检测请求类
 */
public class Request {
    private String server;
    private String path;
    private String url;
    private Map<String,String> header;
    private String entity;
    private String method;
    private Map<String ,String> pathParameters;
    private Map<String ,String> queryParameters;

    public Request(String server,String path,String method, Map<String, String> header, Map<String, String> pathParameters,Map<String, String> queryParameters,String entity){
        this.server=server;
        this.path=path;
        this.method=method;
        this.header=header;
        this.entity=entity;
        this.pathParameters=pathParameters;
        this.queryParameters=queryParameters;
    }
    public Request(Request request){
        this.path=request.getPath();
        this.url=request.getUrl();
        this.header=request.getHeader();
        this.entity=request.getEntity();
        this.method=request.getMethod();
        this.pathParameters=request.getPathParameters();
        this.queryParameters=request.getQueryParameters();
    }
    public Request clone() {
        Map<String,String> headertemp=new HashMap<>();
        for(Map.Entry<String,String> header:header.entrySet()){
            headertemp.put(header.getKey(),header.getValue());
        }
        Map<String,String> pathParameterstemp=new HashMap<>();
        for(Map.Entry<String,String> header:pathParameters.entrySet()){
            headertemp.put(header.getKey(),header.getValue());
        }
        Map<String,String> queryParameterstemp=new HashMap<>();
        for(Map.Entry<String,String> header:queryParameters.entrySet()){
            headertemp.put(header.getKey(),header.getValue());
        }
        Request request= new Request(server,path,method,headertemp,pathParameterstemp,queryParameterstemp,entity);
        request.setUrl(url);
        return request;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(Map<String, String> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHeader(Map<String, String> header) {
        this.header = header;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public String getEntity() {
        return entity;
    }

    /**
     * 构建url,替换路径属性，添加查询属性
     */
    public void buildURL(){
        String requestPath=path;
        //替换路径属性
        if(!pathParameters.isEmpty()){
            for(Map.Entry<String,String> para:pathParameters.entrySet()){
                requestPath = requestPath.replace("{" + para.getKey() + "}", para.getValue());
            }
        }
        //添加查询属性
        if(!queryParameters.isEmpty()){//拼接查询属性到url中
            String querPart="";
            for(Map.Entry<String,String> para:queryParameters.entrySet()){
                querPart+=para.getKey()+"="+para.getValue()+"&";
            }
            querPart="?"+querPart;
            querPart=querPart.substring(0,querPart.length()-1);
            requestPath+=querPart;
        }
        url=server+requestPath;
    }
}
