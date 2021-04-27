package io.swagger.handler;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.oas.inflector.models.RequestContext;
import io.swagger.oas.inflector.models.ResponseContext;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
/*import org.json.JSONException;
import org.json.JSONObject;*/

import javax.json.JsonArray;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class fileHandle {
    private List<List<String>> basicInfos=new ArrayList<>();//name,path,endpoint
    private List<List<String>> hierarchys=new ArrayList<>();//name,hierarchy..
    private List<List<String>> validations=new ArrayList<>();//name,no_,lowcase,nosuffix,noCRUD,noAPI,noversion,noend/
    private List<List<String>> categories=new ArrayList<>();//name,category
    private List<List<String>> securities=new ArrayList<>();//name,securityScheme
    private List<List<String>> CRUDs=new ArrayList<>();//name,CRUD..
    private List<List<String>> suffixs=new ArrayList<>();//name,suffix..
    private List<List<String>> paths=new ArrayList<>();//name,path..
    private List<List<String>> paras=new ArrayList<>();//name,paraName..
    private List<List<String>> CRUDPathOperations=new ArrayList<>();//出现动词的路径使用的操作
    private List<List<String>> versionLocations=new ArrayList<>();//版本信息出现位置的统计
    private List<List<String>> hasAccepts=new ArrayList<>();
    private List<List<String>> hasapiInhosts=new ArrayList<>();
    private List<List<String>> hasCacStatics=new ArrayList<>();
    private List<List<String>> isHATEOAS=new ArrayList<>();
    private List<List<String>> hasResponseContentType=new ArrayList<>();
    private List<List<String>> hasContextedRelations=new ArrayList<>();

    public static void main(String[] args) throws Exception {
//        validateFiles("D:\\test\\data-all-clear");
        /*fileHandle fileHandle=new fileHandle();
        fileHandle.validateFiles("D:\\test\\data-all-clear");*/

        /*RequestConfig.Builder requestBuilder = RequestConfig.custom();//设置配置信息
        requestBuilder = requestBuilder
                .setConnectTimeout(5000)//连接超时时间
                .setSocketTimeout(5000);//socket超时时间
        HttpPost httpRequest=new HttpPost("https://api.github.com/gists");
        httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中
        httpRequest.setHeader("authorization","token 7d3d79e8be31ca6a367b1920acf5bd3bbd119881");
        httpRequest.setHeader("Accept", "application/json, ");//设置请求头文件
        final CloseableHttpClient httpClient = getCarelessHttpClient(false);//创建HTTP客户端
        if (httpClient != null) {
            final CloseableHttpResponse response = httpClient.execute(httpRequest);
            dynamicValidateByResponse(response,urlString,pathResult);
            httpClient.close();
        } else {
            throw new IOException("CloseableHttpClient could not be initialized");
        }
        //设置消息体
        JSONObject jsonObject=JSONObject.fromObject(request.getEntity());
        String string = jsonObject.toString();
        System.out.println("entity: "+string);
        StringEntity entity = new StringEntity(string, "UTF-8");
        httpRequest.setEntity(entity);*/

        ValidatorController validator = new ValidatorController();
        /*String html=validator.getUrlContents("https://www.hs.net/wiki/api/598_quote_v1_stare_query_right_list.html");
        validator.validateByHengShengEndpoint(html);
        System.out.println(validator.getScore());*/
        String html1=validator.getUrlContents("https://www.hs.net/wiki/service/598.html");
        validator.validateByHengSheng(html1);
        System.out.println(validator.getScore());
        /*  //动态检测实验测试2021.4.10
        ValidatorController validator = new ValidatorController();
        //String content=validator.readFile("D:\\test\\data-all-clear\\github.com-v3-swagger.yaml");
        String content=validator.readFile("D:\\test\\data-all-clear\\github.com-v3-swagger.yaml");

        //静态检测
        validator.validateByString(new RequestContext(), content);
        Map<String, Map<String, String>> map = validator.getPathParameterMap();
        System.out.println(validator.getScore());
        //动态检测
        validator.dynamicValidateByContent(content);
        Map<String,Object> pathdetaildynamic=validator.getPathDetailDynamic();
        JSONObject jsonObject=JSONObject.fromObject(pathdetaildynamic);*/


//        System.out.println("pathDetail-dynamic:"+string);
        System.out.println("responseNum:"+validator.getResponseNum());
        System.out.println("valideResponseNum:"+validator.getValidResponseNum());
/*String string = jsonObject.toString();//消息体字符串
        File csvFile = null;
        BufferedWriter csvFileOutputStream = null;
        try {
            File file = new File("D:\\REST API file\\result");
            if (!file.exists()) {
                if (file.mkdirs()) {
                    System.out.println("创建成功");
                } else {
                    System.out.println("创建失败");
                }
            }
            //定义文件名格式并创建
            csvFile = File.createTempFile("pathDetailDynamic", ".csv", new File("D:\\REST API file\\result"));
            csvFileOutputStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8), 1024);
            for (String endpointname:pathdetaildynamic.keySet()) {
                Map<String,Object> pathmap= (Map<String, Object>) pathdetaildynamic.get(endpointname);
                csvFileOutputStream.write(endpointname+",");
                csvFileOutputStream.write(pathmap.get("url").toString()+",");
                csvFileOutputStream.write(pathmap.get("method").toString()+",");
                String requestheader=JSONObject.fromObject(pathmap.get("header")).toString();
                requestheader="\"" + requestheader.replaceAll("\"", "\"\"")+ "\"";
                csvFileOutputStream.write(requestheader+",");
                String entitystring;
                if(pathmap.containsKey("entity")){
                    entitystring="\"" + pathmap.get("entity").toString().replaceAll("\"", "\"\"")+ "\"";
                }else{
                    entitystring="";
                }
                csvFileOutputStream.write(entitystring+",");
                csvFileOutputStream.write(pathmap.get("status").toString()+",");
                String resentitystring;
                if(pathmap.containsKey("responseEntity")){
                    resentitystring="\"" + pathmap.get("responseEntity").toString().replaceAll("\"", "\"\"")+ "\"";
                }else{
                    resentitystring="";
                }
                csvFileOutputStream.write(resentitystring+",");
                csvFileOutputStream.write(pathmap.getOrDefault("isHATEOAS-dy","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("hasCacheScheme","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("hasCacheControl","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("hasEtag","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("hasDate","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("hasExpires","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("hasLastModified","").toString()+",");

                csvFileOutputStream.write(pathmap.getOrDefault("hasContentType","").toString()+",");
                csvFileOutputStream.write(pathmap.getOrDefault("contentType","").toString());
//                csvFileOutputStream.write(",");
//                writeRow(exportDatum, csvFileOutputStream);
                csvFileOutputStream.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (csvFileOutputStream != null) {
                    csvFileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }*/

        //System.out.println(validator.isVersionInHead());
        //System.out.println(validator.isVersionInQueryPara());
        /*//统计有类别信息的API document个数
        ObjectMapper YamlMapper = Yaml.mapper();
        File file = new File("D:\\test\\data-all-clear");
        ArrayList<File> fileList = getListFiles(file);
        int num=0;
        for(File f:fileList){
            ValidatorController validator = new ValidatorController();
            String content=validator.readFile(f.getPath());
            JsonNode jn = YamlMapper.readTree(content); //解析json/yaml格式，生成树结构
            if(jn.get("info").get("x-apisguru-categories")!=null){
                num++;
            }
            System.out.println(num);
        }*/
        /*for(File f : fileList){
            ValidatorController validator = new ValidatorController();
            String path=f.getPath();
            String name=f.getName();
            String content=validator.readFile(path);
            System.out.println(name+" start!");
            ResponseContext response = validator.validateByString(new RequestContext(), content);
*/
        return ;
    }

    /**
    *@Description: 检索并删除字符串中的指定字符串列表
    *@Param: [p, delList]
    *@return: java.lang.String
    *@Author: zhouxinyu
    *@date: 2020/7/21
    */
    public static String delListFromString(String p, String[] delList) {
        String result=p;
        for(int i=0;i<delList.length;i++){
            int start=result.indexOf(delList[i]);
            if(start>=0){
                String temp=result.substring(0,start)+result.substring(start+delList[i].length());
                result=temp;
            }
        }
        return result;
    }

    /**
     * 递归查找文件
     * @param baseDirName  查找的文件夹路径
     * @param targetFileName  需要查找的文件名
     */
    public static File findFiles(String baseDirName, String targetFileName) throws IOException {

        File baseDir = new File(baseDirName);		// 创建一个File对象
        if (!baseDir.exists() || !baseDir.isDirectory()) {	// 判断目录是否存在
            System.out.println("文件查找失败：" + baseDirName + "不是一个目录！");
        }
        String tempName = null;
        //判断目录是否存在
        File tempFile;
        File[] files = baseDir.listFiles();
        for (int i = 0; i < files.length; i++) {
            tempFile = files[i];
            if(tempFile.isDirectory()){
                findFiles(tempFile.getAbsolutePath(), targetFileName);
            }else if(tempFile.isFile()){
                tempName = tempFile.getName();
                if(tempName.equals(targetFileName)){
                    System.out.println("find!"+tempFile.getAbsoluteFile().toString());
                    String pathname="E:\\test\\openapi\\"+tempFile.getAbsolutePath().toString().replace('\\','-').substring(42);
                    File dest=new File(pathname);
                    Files.copy(tempFile.getAbsoluteFile().toPath(),dest.toPath());
                    return tempFile.getAbsoluteFile();
                }
            }
        }
        return null;
    }

    public boolean resultAnalysis(String filePath) throws IOException {
        boolean result=false;
        File baseDir = new File(filePath);		// 创建一个File对象
        if (!baseDir.exists() || !baseDir.isDirectory()) {	// 判断目录是否存在
            System.out.println("文件查找失败：" + filePath + "不是一个目录！");
            return false;
        }
        List<String> files = new ArrayList<String>();
        File file = new File(filePath);
        File[] fileList = file.listFiles();

        for (int i = 0; i < fileList.length; i++) {
            if (fileList[i].isFile()) {
                JsonNode jsonNode= Json.mapper().readTree(fileList[i].toString());
                jsonNode.get("pathNum");
                files.add(fileList[i].toString());
                //文件名，不包含路径
                //String fileName = tempList[i].getName();
            }
        }

        return result;
    }

    /**
    *@Description: 检验文件夹内说明文档
    *@Param: [pathName] 检验指定文件夹
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/6/22
    */
    public static void validateFiles(String pathName) throws Exception {
        //File file = new File("D:\\REST API file\\result");
        //记录执行时间
        File csvFile = File.createTempFile("pathDetailDynamic", ".csv", new File("D:\\REST API file\\result"));
        BufferedWriter csvFileOutputStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8), 1024);

        long startMili=System.currentTimeMillis();// 当前时间对应的毫秒数
        System.out.println("start "+startMili);
        File file = new File(pathName);
        //File[] tempList = file.listFiles();
        ArrayList<File> fileList = getListFiles(pathName);
        Map<String,Object> statuss=new HashMap<>();
        int RADcount=0;
        int PathCount=0;
        for(File f : fileList){
            ValidatorController validator = new ValidatorController();
            String path=f.getPath();
            String name=f.getName();
            csvFileOutputStream.write(name+",");
            long startRAD=System.currentTimeMillis();//一个说明文档的开始时间
            String content=validator.readFile(path);
            System.out.println(name+" start!");
            RADcount++;
            ResponseContext response = validator.validateByString(new RequestContext(), content);
            //ResponseContext response = validator.validateByUrl(new RequestContext(), url);
            PathCount+=validator.getPathNum();
            long endRAD=System.currentTimeMillis();//一个说明文档的结束时间
            csvFileOutputStream.write(validator.getPathNum()+",");//路径数
            csvFileOutputStream.write(endRAD-startRAD+",");//该API检测用时
            csvFileOutputStream.write((endRAD-startRAD)/validator.getPathNum()+",");//该API路径检测的平均用时
            csvFileOutputStream.newLine();
            /*
            //静态检测有无缓存机制
            List<String> cacheStatic=new ArrayList<>();
            cacheStatic.add(name);
            cacheStatic.add(String.valueOf(validator.isHasStrongCacheStatic()));
            cacheStatic.add(String.valueOf(validator.isHasEtagStatic()));
            hasCacStatics.add(cacheStatic);*/
            /*//状态码统计
            statuss.put(name,validator.getStatus());
            statuss.put(name,validator.getStatusUsage());*/

            /*基本信息（路径、端点、get，post，delete，put，head，patch）*/
            /*List<String> basicInfo=new ArrayList<>();
            basicInfo.add(name);
            basicInfo.add(Float.toString(validator.getPathNum()));
            basicInfo.add(Float.toString(validator.getEndpointNum()));
            basicInfo.add(Float.toString(validator.getOpGet()));
            basicInfo.add(Float.toString(validator.getOpPost()));
            basicInfo.add(Float.toString(validator.getOpDelete()));
            basicInfo.add(Float.toString(validator.getOpPut()));
            basicInfo.add(Float.toString(validator.getOpHead()));
            basicInfo.add(Float.toString(validator.getOpPatch()));
            basicInfo.add(Float.toString(validator.getOpOptions()));
            basicInfo.add(Float.toString(validator.getOpTrace()));
            System.out.println(basicInfo.toString());
            basicInfos.add(basicInfo);*/

            /*层级信息*/
            /*List<String> hierarchyInfo=new ArrayList<>();
            hierarchyInfo.add(name);
            hierarchyInfo.add(Float.toString(validator.getAvgHierarchy()));//平均层级数
            hierarchyInfo.add(Float.toString(validator.getPathEvaData()[8]));//最大层级数
            hierarchyInfo.addAll(validator.getHierarchies());
            hierarchys.add(hierarchyInfo);*/
            //System.out.println(validator.evaluations.toString());

            /*命名验证结果*/
            /*List<String> validation=new ArrayList<>();
            validation.add(name);
            validation.add(validator.evaluations.get("noUnderscoreRate"));
            validation.add(validator.evaluations.get("lowcaseRate"));
            validation.add(validator.evaluations.get("noSuffixRate"));
            validation.add(validator.evaluations.get("noCRUDRate"));
            validation.add(validator.evaluations.get("noapiRate"));
            validation.add(validator.evaluations.get("noVersionRate"));
            validation.add(validator.evaluations.get("noEndSlashRate"));
            validations.add(validation);*/

            /*List<String> crudtemp=new ArrayList<>();
            crudtemp.add(name);
            crudtemp.addAll(validator.getCRUDlist());
            CRUDs.add(crudtemp);*/

            /*List<String> pathtemp=new ArrayList<>();
            pathtemp.add(name);
            pathtemp.addAll(validator.getPathlist());
            paths.add(pathtemp);*/

            /*//功能性查询参数统计
            List<String> paratemp=new ArrayList<>();
            paratemp.add(name);
            paratemp.addAll(validator.getQuerypara());
            paras.add(paratemp);*/

           /* List<String> suffixtemp=new ArrayList<>();
            suffixtemp.add(name);
            suffixtemp.addAll(validator.getSuffixlist());
            suffixs.add(suffixtemp);*/

            //validator.resultToFile(name);

            //类别信息统计
            /*List<String> cate=new ArrayList<>();
            if(validator.getCategory()!=null){
                cate.add(name);
                cate.add(validator.getCategory());
                categories.add(cate);
            }*/

            /*安全方案信息*/
            /*List<String> secu=new ArrayList<>();
            secu.add(name);
            secu.addAll(validator.getSecurity());
            securities.add(secu);

            System.out.println(name+" end!");*/

            /*//出现动词以及对应的操作
            if(validator.getCRUDPathOperations()!=null){


                for (List<String> op : validator.getCRUDPathOperations()){
                    List<String> ops=new ArrayList<>();
                    ops.add(name);
                    ops.addAll(op);
                    System.out.println(ops);
                    CRUDPathOperations.add(ops);
                }
            }*/

            /*//版本信息的位置
            List<String> versionLocation=new ArrayList<>();
            versionLocation.add(name);
            //versionLocation.add(String.valueOf(validator.isVersionInHead()));//版本信息在头文件
            //versionLocation.add(String.valueOf(validator.isVersionInQueryPara()));//在查询参数
            versionLocation.add(String.valueOf(validator.isHasVersionInHost()));//在域名中
            versionLocation.add(String.valueOf(validator.getDotCountInServer()));
            versionLocation.add(String.valueOf(validator.getDotCountInPath()));
            versionLocation.add(String.valueOf(validator.isSemanticVersion()));
            versionLocations.add(versionLocation);*/
           /* //是否实现HATEOAS原则
            List<String> isH=new ArrayList<>();
            isH.add(name);
            isH.add(String.valueOf(validator.isHateoas()));//是否实现HATEOAS原则
            isHATEOAS.add(isH);*/

            /*//响应头文件中是否有contenetTyp
            List<String> hascontent=new ArrayList<>();
            hascontent.add(name);
            hascontent.add(String.valueOf(validator.isHasResponseContentType()));//是否响应头文件中是否有contenetType
            hasResponseContentType.add(hascontent);*/
            /*List<String> hascontextedr=new ArrayList<>();
            hascontextedr.add(name);
            hascontextedr.add(String.valueOf(validator.isHasContextedRelation()));//是否响应头文件中是否有contenetType
            hasContextedRelations.add(hascontextedr);*/

     /*       //头文件（accept、身份验证信息（key、token、authoriaztion）实验
            List<String> hasAccept=new ArrayList<>();
            hasAccept.add(name);
            hasAccept.add(String.valueOf(validator.isHasAccept()));//头文件中是否有accpet
            hasAccept.add(String.valueOf(validator.isSecurityInHeadPara()));//头文件中是否有身份验证信息（key、token、authoriaztion）
            hasAccepts.add(hasAccept);

            //头文件（accept、身份验证信息（key、token、authoriaztion）实验
            List<String> hasapiInhost=new ArrayList<>();
            hasapiInhost.add(name);
            hasapiInhost.add(String.valueOf(validator.isApiInServer()));
            hasapiInhosts.add(hasapiInhost);*/
            System.out.println(name+" end!");
        }
        //基本信息（路径、端点）
        //createCSVFile(basicInfos,"result","pathValidate-all");
        //安全信息
        //createCSVFile(securities,"result","security-v2.0");
        //命名验证结果
        //createCSVFile(validations,"result","validationRate-openAPIv2.0");
        //类别信息x-apisguru-categories
        //createCSVFile(categories,"result","category-openAPIv2.0");
        //CRUD统计
        //createCSVFile(CRUDs,"result","CRUD-all");
        //路径统计
        //createCSVFile(paths,"D:\\REST API\\result","path-all");
        //后缀统计
        //createCSVFile(suffixs,"result","suffix-all");
        //查询参数统计
        //createCSVFile(paras,"D:\\REST API\\result","queryPara-all");
        //出现动词的路径使用的操作
        //createCSVFile(CRUDPathOperations,"D:\\REST API\\result","CRUDPathOperations-allV2");
        //统计版本信息出现的位置
        //createCSVFile(this.versionLocations,"D:\\REST API file\\result","versionLocationDot-all");
        //HATEOAS
        //createCSVFile(this.isHATEOAS,"D:\\REST API file\\result","hateoas-all");
        //响应头里有无content-type
        //createCSVFile(this.hasResponseContentType,"D:\\REST API file\\result","contentTypeResponse-all");
        //createCSVFile(this.hasContextedRelations,"D:\\REST API file\\result","contextedRelation-all");
        /*   createCSVFile(this.hasAccepts,"D:\\REST API file\\result","headers(accept/token)-all");
        createCSVFile(this.hasapiInhosts,"D:\\REST API file\\result","apiInHost-all");
*/
        /*//状态码统计
        FileOutputStream outStream = new FileOutputStream("D:\\REST API file\\result\\statusUsage-all.json");
        JSONObject jo=JSONObject.fromObject(statuss);
        outStream.write(jo.toString().getBytes("UTF-8"));*/
        //createCSVFile(hasCacStatics,"D:\\REST API\\result","hasCacheStatic-all");
        long endMili=System.currentTimeMillis();
        System.out.println("end "+endMili);
        System.out.println("总耗时为："+(endMili-startMili)+"毫秒");
        System.out.println("总API数："+RADcount);
        System.out.println("总路径数："+PathCount);

    }

    public File createCSVFile(List<List<String>> exportData, String outPutPath, String fileName) {
        File csvFile = null;
        BufferedWriter csvFileOutputStream = null;
        try {
            File file = new File(outPutPath);
            if (!file.exists()) {
                if (file.mkdirs()) {
                    System.out.println("创建成功");
                } else {
                    System.out.println("创建失败");
                }
            }
            //定义文件名格式并创建
            csvFile = File.createTempFile(fileName, ".csv", new File(outPutPath));
            csvFileOutputStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8), 1024);
            for (List<String> exportDatum : exportData) {
                writeRow(exportDatum, csvFileOutputStream);
                csvFileOutputStream.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (csvFileOutputStream != null) {
                    csvFileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return csvFile;
    }
    /**
    *@Description: 写一行进csv文件
    *@Param: [row, csvWriter]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/6/22
    */
    private void writeRow(List<String> row, BufferedWriter csvWriter) throws IOException {
        int i=0;
        for (String data : row) {
            //csvWriter.write(DelQuota(data));
            csvWriter.write(data);
            if (i!=row.size()-1){
                csvWriter.write(",");
            }
            i++;
        }
    }
    /**
    *@Description: 剔除特殊字符
    *@Param: [str]
    *@return: java.lang.String
    *@Author: zhouxinyu
    *@date: 2020/6/22
    */
    public String DelQuota(String str) {
        String result = str;
        String[] strQuota = {"~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "`", ";", "'", ",", ".", "/", ":", "/,", "<", ">", "?"};
        for (int i = 0; i < strQuota.length; i++) {
            if (result.indexOf(strQuota[i]) > -1)
                result = result.replace(strQuota[i], "");
        }
        return result;
    }

    public static ArrayList<File> getListFiles(Object obj) {
        File directory = null;
        if (obj instanceof File) {
            directory = (File) obj;
        } else {
            directory = new File(obj.toString());
        }
        ArrayList<File> files = new ArrayList<File>();
        if (directory.isFile()) {
            files.add(directory);
            return files;
        } else if (directory.isDirectory()) {
            File[] fileArr = directory.listFiles();
            for (int i = 0; i < fileArr.length; i++) {
                File fileOne = fileArr[i];
                files.addAll(getListFiles(fileOne));
            }
        }
        return files;
    }
    public static JSONObject MaptoJsonObj(Map<String, Object> map, JSONObject resultJson){
        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            resultJson.put(key, map.get(key));
        }
        return resultJson;
    }

    /**
     * 获取指定urlString地址的html字符串
     * @param urlString
     * @return
     * @throws IOException
     */
    public static String getUrlContents(String urlString) throws IOException {
        String html="";
        //1.生成httpclient，相当于该打开一个浏览器
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        //2.创建get请求，相当于在浏览器地址栏输入 网址
        HttpGet request = new HttpGet(urlString);
        try {
            //3.执行get请求，相当于在输入地址栏后敲回车键
            response = httpClient.execute(request);

            //4.判断响应状态为200，进行处理
            if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                //5.获取响应内容
                HttpEntity httpEntity = response.getEntity();
                html = EntityUtils.toString(httpEntity, "utf-8");
//                System.out.println(html);
            } else {
                //如果返回状态不是200，比如404（页面不存在）等，根据情况做处理，这里略
                System.out.println("返回状态不是200");
                System.out.println(EntityUtils.toString(response.getEntity(), "utf-8"));
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //6.关闭
            HttpClientUtils.closeQuietly(response);
            HttpClientUtils.closeQuietly(httpClient);
        }
        return html;
    }
}
