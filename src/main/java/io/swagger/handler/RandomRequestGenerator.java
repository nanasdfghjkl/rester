package io.swagger.handler;

import io.swagger.models.Request;

import java.util.*;

public class RandomRequestGenerator {
    private Request seed;

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
}
