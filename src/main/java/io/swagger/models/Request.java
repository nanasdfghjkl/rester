package io.swagger.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 动态检测请求类
 */
public class Request {
    private String path;
    private String url;
    private Map<String,String> header;
    private String entity;
    private String method;
    private Map<String ,String> pathParameters;
    private Map<String ,String> queryParameters;

    public Request(String path,String method,String url, Map<String, String> header, Map<String, String> pathParameters,Map<String, String> queryParameters,String entity){
        this.method=method;
        this.url=url;
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
}
