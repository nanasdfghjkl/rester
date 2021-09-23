package io.swagger.handler;

import io.swagger.models.Request;

import java.util.*;

public class RandomRequestGenerator {
    private Request seed;
    public static Random random=new Random();
    public RandomRequestGenerator(){
        seed=null;
    }
    public RandomRequestGenerator(Request request){
        this.seed=request;
    }
    public Request getSeed() {
        return seed;
    }

    public void setSeed(Request seed) {
        this.seed = seed;
    }

    public List<Request> requestGenerate(){
        List<Request> requests=new ArrayList<>();
        Map<String,String > headerParas=seed.getHeader();
        Map<String,String > pathParas=seed.getPathParameters();
        Map<String,String > queryParas=seed.getQueryParameters();
        Map<String,String> paras=new HashMap<>();
        paras.putAll(headerParas);
        paras.putAll(pathParas);
        paras.putAll(queryParas);
        String[] keyset=paras.keySet().toArray(new String[0]);
        Random random=new Random();
        for(int i=0;i<10;i++){
            int index=random.nextInt(paras.size());
            String key=keyset[index];
            String value=paras.get(key);
            //变异为空
            Request request1=new Request(seed);
            requests.add(request1);
            // TODO: 2021/6/10  

        }
        return requests;
    }

    /**
     * 随机从路径属性或查询属性中删除一个属性
     * @return
     */
    public Request parameterDelete(){
        int in=random.nextInt(2);
        if(in==0){
            return parameterDelete("path");
        }else {
            return parameterDelete("query");
        }
    }

    /**
     * 随机删除指定位置的一个属性
     * @param in 属性位置（path/query)
     * @return
     */
    public Request parameterDelete(String in){
        Request request =seed.clone();
        if(in.equals("path")){
            Map<String,String > pathParas=seed.getPathParameters();
            int index=random.nextInt(pathParas.size());// 获得一个随机删除的索引号
            Iterator<Map.Entry<String, String>> iterator = pathParas.entrySet().iterator();
            while(index>0){
                iterator.next();
            }
            iterator.remove();
        }else if(in.equals("query")){
            Map<String,String > queryParas=seed.getQueryParameters();
            int index=random.nextInt(queryParas.size());// 获得一个随机删除的索引号
            Iterator<Map.Entry<String, String>> iterator = queryParas.entrySet().iterator();
            while(index>0){
                iterator.next();
            }
            iterator.remove();
        }
        return request;
    }
    public Request parameterType(){
        Request request =seed.clone();
        // TODO: 2021/9/23
        return request;
    }
    public Request parameterFormat(){
        Request request =seed.clone();
        // TODO: 2021/9/23
        return request;
    }

}
