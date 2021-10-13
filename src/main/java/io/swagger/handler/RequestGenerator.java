package io.swagger.handler;

import com.mifmif.common.regex.Generex;
import com.sun.xml.bind.v2.util.QNameMap;
import io.swagger.models.Request;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.*;

public class RequestGenerator {
    private static Request seed;
    public static Random random=new Random();
    ConfigManager configManager=ConfigManager.getInstance();
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
            Iterator<Map.Entry<String,String>> iterator=newRequest.getPathParameters().entrySet().iterator();
            while (iterator.hasNext()){// 遍历路径属性
                //rate概率进行变异
                if(random.nextInt(100)>rate){
                    continue;
                }
                Map.Entry<String,String> paraEntry=iterator.next();
                //指定变异类型
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
            Iterator<Map.Entry<String,String>> iterator1=newRequest.getQueryParameters().entrySet().iterator();
            while (iterator1.hasNext()){// 遍历查询属性
                //rate概率进行变异
                if(random.nextInt(100)>rate){
                    continue;
                }
                Map.Entry<String,String> paraEntry=iterator1.next();
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
            requests.add(newRequest);
            times--;
        }
        return requests;
    }

    /**
     * 头文件属性变异
     * @param fuzzingType 只进行delete变异
     * @param times
     * @param rate
     * @return
     */
    public List<Request> headerFuzzingByRate(String fuzzingType, int times, int rate) {
        List<Request> requests=new ArrayList<>();
        while(times>0){// 变异times次
            Request newRequest=seed.clone();
            Iterator<Map.Entry<String,String>> iterator=newRequest.getHeader().entrySet().iterator();
            while (iterator.hasNext()){// 遍历路径属性
                //rate概率进行变异
                if(random.nextInt(100)>rate){
                    continue;
                }
                Map.Entry<String,String> paraEntry=iterator.next();
                //指定变异类型
                if(fuzzingType.equals("delete")){
                    iterator.remove();
                }
            }
            //变异之后重建请求url，头文件变异不需要重组url
            //newRequest.buildURL();
            requests.add(newRequest);
            times--;
        }
        return requests;
    }

    /**
     * 消息体变异方案
     *
     * @param entitystring
     * @param fuzzingType:delete,type
     * @param times
     * @return
     */
    public List<Request> bodyFuzzing(String entitystring, String fuzzingType, int times) {
        List<Request> requests=new ArrayList<>();
        while(times>0) {// 变异times次
            Request newRequest = seed.clone();
            newRequest.setEntity(entityFuzzing(entitystring,fuzzingType));
            requests.add(newRequest);
            times--;
        }
        return requests;

    }

    /**
     * 消息体元素变异
     * @param entitystring
     * @param fuzzingType
     * @return
     */
    public String entityFuzzing(String entitystring, String fuzzingType) {
        Boolean flag=random.nextBoolean();
        String fuzzingResult="";
        Boolean isArray=false;
        if((entitystring.startsWith("{") || entitystring.startsWith("[")) && flag){//对象类型或数组类型
           //深入子结构变异
            JSONArray jsonArray=new JSONArray();
            if(entitystring.startsWith("[")){
                jsonArray=JSONArray.fromObject(entitystring);
                isArray=true;
            }else if (entitystring.startsWith("{")){
                JSONObject jsonObject=JSONObject.fromObject(entitystring);
                jsonArray.add(jsonObject);
            }
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject object = jsonArray.getJSONObject(i);
                Map map=new HashMap();
                for(Object key:object.keySet()){
                    map.put(key,object.get(key));
                }
                //进行变异
                Iterator<Map.Entry> it=map.entrySet().iterator();
                while(it.hasNext()){
                //for(Object key:object.keySet()){
                    Map.Entry entityEntry=it.next();
                    String value=entityEntry.getValue().toString();
                    //String value=object.get(key).toString();
                    //对于每一个子元素进行嵌套变异
                    String fuzzingEntity=entityFuzzing(value, fuzzingType);//变异后的消息体子结构
                    if(fuzzingEntity==""){
                        //it.remove();
                        object.remove(entityEntry.getKey());
                    }else {
                        entityEntry.setValue(fuzzingEntity);
                        object.put(entityEntry.getKey(),fuzzingEntity);
                    }

                }

            }
            if(isArray){
                fuzzingResult= jsonArray.toString();
            }else {
                fuzzingResult=jsonArray.get(0).toString();
            }


        }else{//基本类型
            //基本类型直接变异
            if(fuzzingType.equals("delete")){
                fuzzingResult= "";
            }else if(fuzzingType.equals("type")){
                fuzzingResult= parameterType(entitystring).toString();
            }
        }

        return fuzzingResult;
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
            Map<String,String > pathParas=request.getPathParameters();
            int index=random.nextInt(pathParas.size());// 获得一个随机删除的索引号
            Iterator<Map.Entry<String, String>> iterator = pathParas.entrySet().iterator();
            while(index>0){
                iterator.next();
            }
            iterator.remove();
        }else if(in.equals("query")){
            Map<String,String > queryParas=request.getQueryParameters();
            int index=random.nextInt(queryParas.size());// 获得一个随机删除的索引号
            Iterator<Map.Entry<String, String>> iterator = queryParas.entrySet().iterator();
            while(index>0){
                iterator.next();
            }
            iterator.remove();
        }
        request.buildURL();
        return request;
    }

    /**
     * 类型变异integer，number(float,double),string,boolean
     * @param oldValue
     * @return
     */
    public Object parameterType(String oldValue){
        Object fuzzingValue="";
        int type=random.nextInt(4);
        //默认值进行变异
        if(type==0){// integer
            fuzzingValue=1;
        }else if(type==1){// number(float,double)
            fuzzingValue=0.1f;
        }else if(type==2){// String
            fuzzingValue="rester";
        }else if(type==3){// boolean
            fuzzingValue=true;
        }
        return fuzzingValue;
    }

    /**
     * 格式变异
     * ①Integer:int32/int64
     * 利用边界值、随机值实现该变异
     * ②Number:float/double
     * 利用边界值、随机值实现该变异
     * ③String:byte/binary/date/自定义含语义信息的格式Email/url等
     * 对每个格式构建小字典来实现变异
     * 从格式的正则表达式生成随机值
     * @param oldValue
     * @return
     */
    public Object parameterFormat(String oldValue){
        Object fuzzingValue="";
        int type=random.nextInt(4);
        //默认值,随机值进行变异
        if(type==0){// integer
            fuzzingValue=random.nextInt();
        }else if(type==1){// number(float,double)
            fuzzingValue=random.nextDouble();
        }else if(type==2){// String
            int stringFormat=random.nextInt(10);
            String[] formats=new String[]{"UUID","DATE","URI","EMAIL","NO"};
            fuzzingValue=formatGenerate(formats[random.nextInt(formats.length)]);
        }else if(type==3){// boolean
            fuzzingValue=random.nextBoolean();
        }
        return fuzzingValue;
    }

    /**
     * 随机生成一个指定格式format的值（字典值或者正则表达式生成）
     * @param format
     * @return
     */
    public String formatGenerate(String format){
        if(format.equals("URI")){
            System.out.println("that's it");
        }
        format=format.toUpperCase();
        String[] values=configManager.getValue(format).split(",");
        if(format.equals("UUID")){// 如果是UUID格式，返回默认字典值或随机生成UUID
            return random.nextBoolean()?values[random.nextInt(values.length)]:UUID.randomUUID().toString();
        }else if(format.equals("NO")){
            return random.nextBoolean()?values[random.nextInt(values.length)]: String.valueOf(random.nextInt());
        }
        String reg="REGEX_"+format;
        // 返回字典值或者使用正则表达式反向生成随机值
        String ans="";
        if(random.nextBoolean()){
            ans=values[random.nextInt(values.length)];
        }else {
            String regex=configManager.getValue(reg);
            ans=new Generex(regex).random();
        }
        return ans;
    }



}
