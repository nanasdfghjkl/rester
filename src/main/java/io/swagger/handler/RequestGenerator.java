package io.swagger.handler;

import io.swagger.models.Request;

import java.util.*;

public class RequestGenerator {
    private Request seed;
    public static Random random=new Random();
    public RequestGenerator(){
        seed=null;
    }
    public RequestGenerator(Request request){
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
     * 对于每一个属性（路径属性+查询属性），有rate（0-100）概率进行fuzzingType（delete，type，format）变异，
     * 变异times次，即生成times个Request
     * @param fuzzingType
     * @param times
     * @param rate
     * @return
     */
    public List<Request> paraFuzzingByRate(String fuzzingType,int times,int rate){
        List<Request> requests=new ArrayList<>();
        while(times>0){// 变异times次
            Request newRequest=seed.clone();
            Iterator<Map.Entry<String,String>> iterator=seed.getPathParameters().entrySet().iterator();
            while (iterator.hasNext()){// 遍历路径属性
                Map.Entry<String,String> paraEntry=iterator.next();
                if(fuzzingType.equals("delete")){
                    iterator.remove();
                }else if(fuzzingType.equals("type")){
                    Object fuzzingValue=parameterType(paraEntry.getValue());
                    newRequest.getPathParameters().put(paraEntry.getKey(),fuzzingValue.toString());
                }else if(fuzzingType.equals("format")){
                    Object fuzzingValue=parameterFormat(paraEntry.getValue());
                    newRequest.getPathParameters().put(paraEntry.getKey(),fuzzingValue.toString());
                }
            }
            Iterator<Map.Entry<String,String>> iterator1=seed.getQueryParameters().entrySet().iterator();
            while (iterator1.hasNext()){// 遍历查询属性
                Map.Entry<String,String> paraEntry=iterator.next();
                if(fuzzingType.equals("delete")){
                    iterator.remove();
                }else if(fuzzingType.equals("type")){
                    Object fuzzingValue=parameterType(paraEntry.getValue());
                    newRequest.getQueryParameters().put(paraEntry.getKey(),fuzzingValue.toString());
                }else if(fuzzingType.equals("format")){
                    Object fuzzingValue=parameterFormat(paraEntry.getValue());
                    newRequest.getQueryParameters().put(paraEntry.getKey(),fuzzingValue.toString());
                }
            }
            //变异之后重建请求url
            newRequest.buildURL();
        }
        return requests;
    }

    /**
     * 随机从路径属性或查询属性中删除一个属性
     * @return
     */
    public Request parameterDelete(){
        int in=random.nextInt(seed.getPathParameters().size()+seed.getQueryParameters().size());
        if(in<seed.getPathParameters().size()){
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

    /**
     * 类型变异integer，number(float,double),string,boolean
     * @param oldValue
     * @return
     */
    public Object parameterType(String oldValue){
        Object fuzzingValue="";
        // TODO: 2021/9/23
        int type=random.nextInt(4);
        //默认值进行变异
        if(type==0){
            fuzzingValue=1;
        }else if(type==1){
            fuzzingValue=0.1;
        }else if(type==2){
            fuzzingValue="rester";
        }else if(type==3){
            fuzzingValue=true;
        }
        return fuzzingValue;
    }
    public Object parameterFormat(String oldValue){
        Request request =seed.clone();
        // TODO: 2021/9/23
        return request;
    }

}
