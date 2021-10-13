package io.swagger.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.report.ListProcessingReport;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.swagger.models.*;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.graph.DependenceGraph;
import io.swagger.models.graph.GraphNode;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.SerializableParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.oas.inflector.models.RequestContext;
import io.swagger.oas.inflector.models.ResponseContext;

import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.parser.util.SwaggerDeserializationResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import io.swagger.v3.parser.util.OpenAPIDeserializer;
//import jdk.internal.access.JavaSecurityAccess;
import net.didion.jwnl.JWNLException;
import net.sf.json.JSONException;
import org.apache.commons.lang3.StringUtils;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
/*import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;*/
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import org.jsoup.select.Elements;

@Service
public class ValidatorController{

    static final String SCHEMA_FILE = "schema3.json";
    static final String SCHEMA_URL = "https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/schemas/v3.0/schema.json";

    static final String SCHEMA2_FILE = "schema.json";
    static final String SCHEMA2_URL = "http://swagger.io/v2/schema.json";

    static final String INVALID_VERSION = "Deprecated Swagger version.  Please visit http://swagger.io for information on upgrading to Swagger/OpenAPI 2.0 or OpenAPI 3.0";

    static Logger LOGGER = LoggerFactory.getLogger(ValidatorController.class);
    static long LAST_FETCH = 0;
    static long LAST_FETCH_V3 = 0;
    static ObjectMapper JsonMapper = Json.mapper();
    static ObjectMapper YamlMapper = Yaml.mapper();
    private JsonSchema schemaV2;
    private JsonSchema schemaV3;

    static boolean rejectLocal = StringUtils.isBlank(System.getProperty("rejectLocal")) ? true : Boolean.parseBoolean(System.getProperty("rejectLocal"));
    static boolean rejectRedirect = StringUtils.isBlank(System.getProperty("rejectRedirect")) ? true : Boolean.parseBoolean(System.getProperty("rejectRedirect"));

    private String name;
    private DependenceGraph dependenceGraph =new DependenceGraph();// 资源依赖图
    private float pathNum;//路径数
    private int endpointNum;//端点数
    private float score=100; //评分机制
    public Map<String,String> evaluations=new HashMap<String, String>();
    private float pathEvaData[] =new float[20];//记录实现各规范的path数 [0 no_,1 lowercase,2 noVersion,3 noapi,4 noCRUD,5 noSuffix,6 noend/,7 sumHierarchy,8 maxHierarchy]
    private float avgHierarchy;//路径平均层级数
    private List<String> hierarchies=new ArrayList<>();//所有路径层级数统计

    private Map<String,Object> validateResult=new HashMap<>(); //检测结果json


    private Map<String,Object> pathDetailDynamic=new HashMap<>();//动态检测的路径结果
    private Map<String,Object> pathDetail=new HashMap<>();//静态检测 的结果

    private boolean hasPagePara = false;//是否有分页相关属性

    private boolean apiInServer=false;//域名中是否有“api”

    private String fileName;
    private String category=null;//类别信息
    private int opGet;//get操作数
    private int opPost;//post操作数
    private int opDelete;//delete操作数
    private int opPut;//put操作数
    private int opHead;//head操作数
    private int opOptions;
    private int opPatch;
    private  List<String> security=new ArrayList<>();//支持的安全方案
    private  List<String> CRUDlist=new ArrayList<>();//出现的动词列表
    private List<String> suffixlist=new ArrayList<>();//出现的后缀列表
    private List<String> pathlist=new ArrayList<>();//路径
    private List<String> querypara=new ArrayList<>();//过滤、条件限制、分页查询参数
    private List<List<String>> CRUDPathOperations=new ArrayList<>();//出现动词的路径使用的操作

    String contentType="";
    private boolean hasCacheScheme=false;//是否有缓存机制
    private boolean hasStrongCacheStatic=false;//是否有强制缓存机制cache-control、expires、date-静态检测
    private boolean hasCacheControlStatic=false;//是否有强制缓存机制cache-control-静态检测
    private boolean hasExpiresStatic=false;//是否有强制缓存机制expires-静态检测
    private boolean hasDateStatic=false;//是否有强制缓存机制date-静态检测
    private boolean hasNegCacheStatic=false;//是否有协商缓存机制静态检测
    private boolean hasEtagStatic=false;//是否有协商缓存机制etag静态检测
    private boolean hasLastModifiedStatic=false;//是否有协商缓存机制 last-modified静态检测

    private boolean hasStrongCache=false;//是否有强制缓存机制cache-control、expires、date-动态检测
    private boolean hasCacheControl=false;//是否有强制缓存机制cache-control-动态检测
    private boolean hasExpires=false;//是否有强制缓存机制expires-动态检测
    private boolean hasDate=false;//是否有强制缓存机制date-动态检测
    private boolean hasNegCache=false;//是否有协商缓存机制-动态检测
    private boolean hasEtag=false;//是否有协商缓存机制etag-动态检测
    private boolean hasLastModified=false;//是否有协商缓存机制 last-modified-动态检测

    private boolean hasResponseContentTypeStatic;//是否有响应头content-type-静态检测
    private boolean hasResponseContentType=false;//响应头文件中是否有contenetType



    private boolean versionInPath=false;//路径中是否有属性
    private boolean versionInQueryPara=false;//查询属性中是否有版本信息（版本信息不应该出现在查询属性中）
    private boolean versionInHead=false;//头文件中是否有版本信息
    private boolean versionInHost=false;//服务器信息/域名中是否有版本信息

    private Map<String,Integer> status=new HashMap<>();//状态码使用情况的统计
    private int[] statusUsage;//状态码使用情况（端点级别 是否使用各类状态码
    private int dotCountInServer;//server中版本号的.数，用来判断是否语义版本号
    private int dotCountInPath;//path中版本号的.数，用来判断是否语义版本号
    private boolean semanticVersion=false;//是否使用语义版本号
    private boolean hateoas=false;//是否实现HATEOAS原则-动态
    private boolean hateoasStatic;//是否实现HATEOAS原则-静态检测

    private boolean hasContextedRelation=false;//是否有符合层级关系的路径
    private boolean hasAccept=false;//头文件（属性）中是否有accept
    private boolean securityInHeadPara=false;//头文件（属性）中是否有安全验证机制
    private boolean hasKey=false;//头文件（属性）中是否有key
    private boolean hasToken=false;//头文件（属性）中是否有Token
    private boolean hasAuthorization=false;//头文件（属性）中是否有TokenAuthorization

    private Map<String,PathTreeNode> pathTree;//路径结点树（森林）
    private Map<String,Map<String,List<String> >> pathParameterMap=new HashMap<>();//属性散列表，path-parameter-values
    private int responseNum=0;//获得的响应数目
    private int validResponseNum=0;//获得的有效响应数目（2字头）
    private boolean hasWrongStatus=false;//是否有HTTP协议定义外的状态码
    private boolean hasWrongPost=false;//是否有对POST方法的误用

//    @Value("${UUID}")
    private String uuid;
//    @Value("${DATE}")
    private String date;
//    @Value("${URL}")
    private String url;
//    @Value("${KEY}")
    private String key;
//    @Value("${TOKEN}")
    private String token;
//    @Value("${NO}")
    private String no;
//    @Value("${EMAIL}")
    private String email;
    private String owner;
    private String user;
    private String language;
    private String org;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public int getResponseNum() {
        return responseNum;
    }

    public int getValidResponseNum() {
        return validResponseNum;
    }

    public void setValidateResult() {
        validateResult.put("name",this.name);
        validateResult.put("securityList",getSecurity());



        validateResult.put("hasCacheControl",this.hasCacheControlStatic);
        validateResult.put("hasDate",this.hasDateStatic);
        validateResult.put("hasExpires",this.hasExpiresStatic);
        validateResult.put("hasStrongCache",this.hasStrongCacheStatic);
        validateResult.put("hasEtag",this.hasEtagStatic);
        validateResult.put("hasLastModified",this.hasLastModifiedStatic);
        validateResult.put("hasNegCache",this.hasNegCacheStatic);

        validateResult.put("hateoas",this.hateoasStatic);
        validateResult.put("hateoas-dy",this.hateoas);

        validateResult.put("hasPagePara",isHasPagePara());
        validateResult.put("pageParaList",getQuerypara());

        validateResult.put("hasSecurityInHeadPara",this.securityInHeadPara);
        validateResult.put("hasKey",this.hasKey);
        validateResult.put("hasToken",this.hasToken);
        validateResult.put("hasAuthorization",this.hasAuthorization);
        validateResult.put("hasAccpet",this.hasAccept);

        validateResult.put("apiInServer",this.apiInServer);
        validateResult.put("versionInHost",this.versionInHost);
        validateResult.put("versionInPath",this.versionInPath);
        validateResult.put("versionInQueryPara",this.versionInQueryPara);
        validateResult.put("versionInHeader",this.versionInHead);
        validateResult.put("semanticVersion",this.semanticVersion);

        validateResult.put("avgHierarchies",getAvgHierarchy());

        validateResult.put("statusUsage",this.statusUsage);
        validateResult.put("status",this.status);

        validateResult.put("contextualizedPath",this.hasContextedRelation);
        validateResult.put("path",pathDetail);

        validateResult.put("category",getCategory());
        validateResult.put("pathNum",getPathNum());
        validateResult.put("endpointNum",this.getEndpointNum());
        validateResult.put("opGET",this.getOpGet());
        validateResult.put("opPOST",this.getOpPost());
        validateResult.put("opDELETE",getOpDelete());
        validateResult.put("opPUT",getOpPut());
        validateResult.put("opHEAD",getOpHead());
        validateResult.put("opPATCH",getOpPatch());
        validateResult.put("opOPTIONS",getOpOptions());
        validateResult.put("opTRACE",getOpTrace());

        validateResult.put("hasWrongStatus",this.hasWrongStatus);
        validateResult.put("hasWrongPost",this.hasWrongPost);

        validateResult.put("score",this.score);
    }

    public Map<String, Object> getPathDetailDynamic() {
        return pathDetailDynamic;
    }

    public Map<String, Map<String, List<String>>> getPathParameterMap() {
        return pathParameterMap;
    }

    public boolean isHasContextedRelation() {
        return hasContextedRelation;
    }

    public boolean isHasResponseContentType() {
        return hasResponseContentType;
    }

    public boolean isHateoas() {
        return hateoas;
    }

    public boolean isSemanticVersion() {
        return semanticVersion;
    }

    public int getDotCountInServer() {
        return dotCountInServer;
    }

    public int getDotCountInPath() {
        return dotCountInPath;
    }

    public boolean isHasStrongCacheStatic() {
        return hasStrongCacheStatic;
    }

    public boolean isHasEtagStatic() {
        return hasEtagStatic;
    }

    public int[] getStatusUsage() {
        return statusUsage;
    }

    public Map<String, Integer> getStatus() {
        return status;
    }

    public boolean isApiInServer() {
        return apiInServer;
    }

    public boolean isVersionInHost() {
        return versionInHost;
    }

    public boolean isHasAccept() {
        return hasAccept;
    }

    public boolean isHasCacheScheme() {
        return hasCacheScheme;
    }

    public boolean isVersionInQueryPara() {
        return versionInQueryPara;
    }

    public boolean isVersionInHead() {
        return versionInHead;
    }

    public boolean isSecurityInHeadPara() {
        return securityInHeadPara;
    }

    public Map<String, Object> getValidateResult() {
        return validateResult;
    }
    public Map<String, Object> getPathDetail() {
        return pathDetail;
    }
    public List<List<String>> getCRUDPathOperations() {
        return CRUDPathOperations;
    }

    public List<String> getQuerypara() {
        return querypara;
    }

    public List<String> getPathlist() {
        return pathlist;
    }

    public List<String> getCRUDlist() {
        return CRUDlist;
    }

    public List<String> getSuffixlist() {
        return suffixlist;
    }

    public List<String> getSecurity() {
        return security;
    }

    public int getOpTrace() {
        return opTrace;
    }

    private int opTrace;//3.0规范特有

    public int getOpGet() {
        return opGet;
    }

    public int getOpPost() {
        return opPost;
    }

    public int getOpDelete() {
        return opDelete;
    }

    public int getOpPut() {
        return opPut;
    }

    public int getOpHead() {
        return opHead;
    }

    public int getOpOptions() {
        return opOptions;
    }

    public int getOpPatch() {
        return opPatch;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getHierarchies() {
        return hierarchies;
    }

    public int getEndpointNum() {
        return endpointNum;
    }

    public void setEndpointNum(int endpointNum) {
        this.endpointNum = endpointNum;
    }

    public float getPathNum() {
        return pathNum;
    }

    public void setPathNum(int pathNum) {
        this.pathNum = pathNum;
    }

    public float getAvgHierarchy() {
        return avgHierarchy;
    }

    public void setAvgHierarchy(float avgHierarchy) {
        this.avgHierarchy = avgHierarchy;
    }

    public boolean isHasPagePara() {
        return hasPagePara;
    }
    public float[] getPathEvaData() {
        return pathEvaData;
    }
    public void setHasPagePara(boolean hasPagePara) {
        this.hasPagePara = hasPagePara;
    }

    public ValidatorController(){
        uuid=ConfigManager.getInstance().getValue("UUID");
        date=ConfigManager.getInstance().getValue("date");
        url=ConfigManager.getInstance().getValue("URL");
        key =ConfigManager.getInstance().getValue("KEY");
        token =ConfigManager.getInstance().getValue("TOKEN");
        no =ConfigManager.getInstance().getValue("NO");
        email=ConfigManager.getInstance().getValue("EMAIL");
        owner=ConfigManager.getInstance().getValue("OWNER");
        user=ConfigManager.getInstance().getValue("USER");
        language=ConfigManager.getInstance().getValue("LANGUAGE");
        org = ConfigManager.getInstance().getValue("ORG");
    }

    public float getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public ResponseContext validateByUrl(RequestContext request , String url) {

        this.fileName=url;

        if(url == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByUrl(request, url);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process URL" );
        }

        //System.out.println("message:"+validationResponse.getMessages());

        return processValidationResponse(validationResponse);
    }

    /**
    *@Description: 直接检测说明文档（String）
    *@Param: [request, content]
    *@return: io.swagger.oas.inflector.models.ResponseContext
    *@Author: zhouxinyu
    *@date: 2020/5/16
    */
    public ResponseContext validateByString(RequestContext request, String content){
        if(content == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied.  Try again?" );
        }

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByContent(request, content);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to get content" );
        }

       // System.out.println("message:"+validationResponse.getMessages());

        return processValidationResponse(validationResponse);
    }

    public static void readToBuffer(StringBuffer buffer, String filePath) throws IOException {
        InputStream is = new FileInputStream(filePath);
        String line; // 用来保存每行读取的内容
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        line = reader.readLine(); // 读取第一行
        while (line != null) { // 如果 line 为空说明读完了
            buffer.append(line); // 将读到的内容添加到 buffer 中
            buffer.append("\n"); // 添加换行符
            line = reader.readLine(); // 读取下一行
        }
        reader.close();
        is.close();
    }
    public static String readFile(String filePath) throws IOException {
        StringBuffer sb = new StringBuffer();
        readToBuffer(sb, filePath);
        return sb.toString();
    }

    public ResponseContext validateByContent(RequestContext request, JsonNode inputSpec) {
        if(inputSpec == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }
        String inputAsString = Json.pretty(inputSpec);

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByContent(request ,inputAsString);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process URL" );
        }


        return processValidationResponse(validationResponse);
    }


    private ResponseContext processValidationResponse(ValidationResponse validationResponse) {
        if (validationResponse == null){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        boolean valid = true;
        boolean upgrade = false;
        List messages = new ArrayList<>();

        if (validationResponse.getMessages() != null) {
            for (String message : validationResponse.getMessages()) {
                if (message != null) {
                    messages.add(message);
                    if(message.endsWith("is unsupported")) {
                        valid = true;
                    }else{
                        valid = false;
                    }
                }
            }
        }
        if (validationResponse.getSchemaValidationMessages() != null) {
            for (SchemaValidationError error : validationResponse.getSchemaValidationMessages()) {
                if (error != null) {
                    messages.add(error.getMessage());
                    if (error.getLevel() != null && error.getLevel().toLowerCase().contains("error")) {
                        valid= false;
                    }
                    if (INVALID_VERSION.equals(error.getMessage())) {
                        upgrade = true;
                    }
                }
            }
        }

        if (upgrade == true ){
            return new ResponseContext()
                    .contentType("image/png")
                    .entity(this.getClass().getClassLoader().getResourceAsStream("upgrade.png"));
        }else if (valid == true ){
            return new ResponseContext()
                    .contentType("image/png")
                    .entity(this.getClass().getClassLoader().getResourceAsStream("valid.png"));
        } else{
            return new ResponseContext()
                    .contentType("image/png")
                    .entity(this.getClass().getClassLoader().getResourceAsStream("invalid.png"));
        }
    }
    public ResponseContext reviewByUrl(RequestContext request , String url) {

        if(url == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByUrl(request, url);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        return new ResponseContext()
                .entity(validationResponse);
        //return processDebugValidationResponse(validationResponse);

    }


    public ResponseContext reviewByContent(RequestContext request, JsonNode inputSpec) {
        if(inputSpec == null) {
            return new ResponseContext()
                    .status(Response.Status.BAD_REQUEST)
                    .entity( "No specification supplied in either the url or request body.  Try again?" );
        }
        String inputAsString = Json.pretty(inputSpec);

        ValidationResponse validationResponse = null;
        try {
            validationResponse = debugByContent(request ,inputAsString);
        }catch (Exception e){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        return new ResponseContext()
                .entity(validationResponse);
        //return processDebugValidationResponse(validationResponse);
    }

    private ResponseContext processDebugValidationResponse(ValidationResponse validationResponse) {
        if (validationResponse == null){
            return new ResponseContext().status(Response.Status.INTERNAL_SERVER_ERROR).entity( "Failed to process specification" );
        }

        List messages = new ArrayList<>();
        if (validationResponse.getMessages() != null) {
            for (String message : validationResponse.getMessages()) {
                if (message != null) {
                    messages.add(message);
                }

            }
        }
        if (validationResponse.getSchemaValidationMessages() != null) {
            for (SchemaValidationError error : validationResponse.getSchemaValidationMessages()) {
                if (error != null) {
                    messages.add(error.getMessage());
                }
            }
        }

        return new ResponseContext()
                .entity(messages);
    }

    public ValidationResponse debugByUrl( RequestContext request, String url) throws Exception {
        ValidationResponse output = new ValidationResponse();
        String content;

        if(StringUtils.isBlank(url)) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage("No valid URL specified");
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }

        // read the spec contents, bail if it fails
        try {
            content = getUrlContents(url, ValidatorController.rejectLocal, ValidatorController.rejectRedirect); //获取url提供的swagger文档，返回响应entity
        } catch (Exception e) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage("Can't read from file " + url);
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }

        return debugByContent(request, content);
    }
    public void dynamicValidateByContent(String content) throws IOException, JSONException {
        JsonNode spec = readNode(content); //解析json/yaml格式，生成树结构
        String version = getVersion(spec);
        if (version != null && (version.startsWith("\"2") || version.startsWith("2"))){
            SwaggerDeserializationResult result = null;
            try {
                result = readSwagger(content);  //根据content构建swagger model
            } catch (Exception e) {
                LOGGER.debug("can't read Swagger contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse Swagger: " + e.getMessage());
            }
            //动态检测，提取url

            List<Scheme> schemes = result.getSwagger().getSchemes();
            if(schemes==null){
                schemes.add(Scheme.HTTP);
            }
            String host=result.getSwagger().getHost()==null?"":result.getSwagger().getHost();
            String basepath=result.getSwagger().getBasePath()==null || result.getSwagger().getBasePath().equals("/")?"":result.getSwagger().getBasePath();
            Set paths = result.getSwagger().getPaths().keySet();
            for(Scheme scheme:schemes){
                for (Iterator it = paths.iterator(); it.hasNext(); ) {
                    String pathString = (String) it.next();
                    Path path=result.getSwagger().getPath(pathString);
                    System.out.println(pathString);
                    /*if(pathString.contains("/repos/{owner}/{repo}/commits/{ref}/status")){
                        System.out.println("that's it");
                    }*/
                    Map<String,io.swagger.models.Operation> operations=getAllOperationsMapInAPath(path);
                    for(String method : operations.keySet()){//对于每一个操作,创建一个请求
                        if(operations.get(method)!=null && method!="delete"){//先不考虑删除操作，会影响资源的存在，因为资源之间的依赖导致请求无效
                            io.swagger.models.Operation operation=operations.get(method);
                            Map<String,String> headers=new HashMap<>();//请求头文件
                            Map<String,Object> entity=new HashMap<>();//请求体
                            List<Object> arrayEntity=new ArrayList<>();
                            String requestPath=pathString;//请求路径

                            List<io.swagger.models.parameters.Parameter> parameters= operation.getParameters();
                            Map<String,String> queryParas=new HashMap<>();//查询参数
                            Map<String,String> pathParas=new HashMap<>();//路径参数
                            if(parameters!=null){
                                for(io.swagger.models.parameters.Parameter parameter:parameters){
                                    /*//Swagger解析时，RefParameter会直接连接到对应属性，并生成对应的实例
                                    if(parameter.getClass().getName()=="RefParameter"){
                                        RefParameter refpara=(RefParameter)parameter;
                                        String ref=refpara.get$ref();
                                    }*/
                                    if(parameter.getRequired()==true){//必需属性
                                        try {
                                            SerializableParameter spara = (SerializableParameter) parameter;//这个子类才能获取到类型、枚举等值,包括header、querty、path、cookie、Form属性

                                            String paraType=spara.getType();
                                            String paraName = parameter.getName();
                                            String paraValue="";//填充后的值
                                            String paraIn=parameter.getIn();

                                            //生成属性值
                                            List<String> paraEnum=spara.getEnum();//获得说明文档中的枚举值
                                            String ppname;
                                            if(paraIn=="path"){
                                                ppname=StanfordNLP.removeBrace(pathString.substring(0,pathString.indexOf("{"+paraName+"}")));
                                            }else{
                                                ppname=StanfordNLP.removeBrace(pathString);
                                            }
                                            ppname=StanfordNLP.removeSlash(ppname);//去除多余/和尾/
                                            String paraMapValue="";
                                            if(pathParameterMap.containsKey(ppname)){
                                                //获取对应的路径属性散列表中的属性值,先只获取第一个
                                                if(pathParameterMap.get(ppname).get(paraName).size()>0){
                                                    paraMapValue=pathParameterMap.get(ppname).get(paraName).get(0);
                                                }

                                            }
                                            //生成属性值：优先级排序：说明文档提供的枚举值，路径属性散列表，类型默认值
                                            if(paraEnum!=null){
                                                paraValue=paraEnum.get(0);
                                            }else if(paraMapValue!=null && paraMapValue.length()!=0){
                                                paraValue=paraMapValue;
                                            }  else{
                                                paraValue=getDefaultFromType(paraType,paraName).toString();
//                                                paraValue=getDefaultStringFromName(paraName).toString();
                                            }

                                            //根据属性位置给请求填充属性

                                            if(paraIn=="path") {//路径属性
                                                requestPath=requestPath.replace("{"+paraName+"}",paraValue);
                                                pathParas.put(paraName,paraValue);
                                            }else if(paraIn=="query"){//查询属性
                                                queryParas.put(paraName,paraValue);
                                                //pathString+="?"+paraName+"="+paraValue;
                                            }else if(paraIn=="header"){
                                                headers.put(paraName,paraValue);
                                            }else if(paraIn=="cookie"){
                                                headers.put("cookie",paraValue);
                                            }

                                        }catch (ClassCastException e){//消息体属性无法反射到SerializableParameter
                                            BodyParameter bodypara=(BodyParameter) parameter;
                                            if(bodypara.getExamples()!=null){//有例子直接使用例子值
                                                for(String k:bodypara.getExamples().keySet()){
                                                    entity.put(k,bodypara.getExamples().get(k));
                                                }
                                            }else if(bodypara.getSchema()!=null){//schema内容描述
                                                Map<String, Property> properties=bodypara.getSchema().getProperties();
                                                if(properties!=null){//schema中直接有properties描述
                                                    entity=parsePropertiesToEntity(properties);
                                                }else if(bodypara.getSchema().getReference()!=null){//为引用属性
                                                    //从definition中获得描述信息
                                                    String ref=bodypara.getSchema().getReference();
                                                    String[] refsplits=ref.split("/");
                                                    if(refsplits[1].equals("definitions")){
                                                        Map<String, Model> defs=result.getSwagger().getDefinitions();
                                                        Model def=defs.get(refsplits[2]);
                                                        Map<String, Property> propertiesFromDef=def.getProperties();
                                                        if(propertiesFromDef!=null){
                                                            entity=parsePropertiesToEntity(propertiesFromDef);//将property生成消息体
                                                        }else{//消息体中没有对参数的描述
                                                            //数组Array类型的definition，没有properties，获取其items
                                                            ArrayModel arrdef= (ArrayModel) def;

                                                            Property items=arrdef.getItems();
                                                            //items类型是object，properties对其进行描述
                                                            if(items.getType().equals("object")){
                                                                ObjectProperty itemsob=(ObjectProperty) items;
                                                                Map<String, Property> propertiesFromItems=itemsob.getProperties();
                                                                entity=parsePropertiesToEntity(propertiesFromItems);
                                                                arrayEntity.add(entity);
                                                            }else{//其他类型（基本类型）
                                                                arrayEntity.add(getDefaultFromType(items.getType(),refsplits[2]));
                                                            }
                                                        }

                                                    }
                                                }else{//消息体中没有对参数的描述
                                                    entity.put("body","rester");
                                                }
                                            }
                                            else{//消息体中没有对参数的描述
                                                entity.put("body","rester");
                                            }
                                        }

                                    }
                                }
                            }
                            if(queryParas.size()!=0){//拼接查询属性到url中
                                String querPart="";
                                for(String paraname:queryParas.keySet()){
                                    querPart+=paraname+"="+queryParas.get(paraname)+"&";
                                }
                                querPart="?"+querPart;
                                querPart=querPart.substring(0,querPart.length()-1);
                                requestPath+=querPart;
                            }
                            String url=scheme.toValue()+"://"+host+basepath+requestPath;
                            //System.out.println(url);
                            //将消息体对象转化为字符串
                            String entitystring="";
                            if(arrayEntity.size()!=0){
                                JSONArray jsonArray=JSONArray.fromObject(arrayEntity);
                                entitystring=jsonArray.toString();
                            }else{
                                JSONObject jsonObject=JSONObject.fromObject(entity);
                                entitystring = jsonObject.toString();
                            }
                            Request request=new Request(pathString,method,url,headers,pathParas,queryParas,entitystring);
                            dynamicValidateByURL(pathString,request,false,false);
                            //属性变异
                            RequestGenerator rrg=new RequestGenerator(request);
                            List<Request> randomRequests=rrg.requestGenerate();
                        }
                    }


                }
            }
            hasStrongCache=hasCacheControl || hasExpires || hasDate;
            hasNegCache=hasEtag || hasLastModified;
        }
        else if (version == null || (version.startsWith("\"3") || version.startsWith("3"))) {
            SwaggerParseResult result = null;
            try {
                result = readOpenApi(content);
            } catch (Exception e) {
                LOGGER.debug("can't read OpenAPI contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse OpenAPI: " + e.getMessage());
            }
            Paths paths = result.getOpenAPI().getPaths();
            Map<String, Parameter> componetParas = result.getOpenAPI().getComponents().getParameters();
            //动态检测，获取URL
            List<Server> servers = result.getOpenAPI().getServers();
            if(servers.size()==1 && servers.get(0).getUrl()=="/"){
                System.out.println("server only has ”/“");
            }else {
                for (Server server : servers) {
                    String serverURL = server.getUrl();//基路径url
                    if(serverURL.contains("{")){
                        boolean paraFlag=true;
                        ServerVariables serverVaris = server.getVariables();
                        List<String> varsInURL = extractMessageByRegular(serverURL);//找到路径中出现的参数
                        for(String varInURL:varsInURL){
                            ServerVariable serverVar = serverVaris.get(varInURL);
                            List<String> varValues = serverVar.getEnum();//提取对应的参数枚举值
                            if(varValues==null || varValues.size()==0){
                                System.out.println(varInURL+"can't find value");
                                paraFlag=false;
                                break;
                            }else{
                                String varValue=varValues.get(0);
                                serverURL=serverURL.replace("{"+varInURL+"}",varValue);//将{参数}替换为枚举值第一个值
                            }

                        }
                        if(!paraFlag){
                            continue;
                        }
                        System.out.println(serverURL);
                    }
                    //Map<Integer,Set<String>> subGs=new HashMap<>();//依赖子图，拷贝一份
                    Map<String,Set<String>> edgesMap=new HashMap<>();// 边集，邻接表,拷贝一份，

                    //拷贝from边集
                    for(Map.Entry<String,Set<String>> fromEdge:dependenceGraph.getEdgesMap().entrySet()){
                        Set<String> temp=new HashSet<>();
                        for(String path:fromEdge.getValue()){
                            temp.add(path);
                        }
                        edgesMap.put(fromEdge.getKey(),temp);
                    }
                    for(Map.Entry<Integer,Set<String>>subGEntry:dependenceGraph.getSubGs().entrySet()){
                        Set<String> subGNode=subGEntry.getValue();
                        Set<String> visitedNodes=new HashSet<>();//记录遍历过的结点
                        //按照拓扑排序将每个子图全部遍历过
                        while(visitedNodes.size()<subGNode.size()){
                            //for(String pathKey:paths.keySet()){
                            for(String pathKey:subGNode){
                                // 跳过访问过的或者存在to点（即有所依赖）的结点
                                if(visitedNodes.contains(pathKey) || edgesMap.containsKey(pathKey)){
                                    continue;
                                }
                                PathItem pathItem=paths.get(pathKey);
                                Map<String, io.swagger.v3.oas.models.Operation> operations=getAllOperationsMapInAPath(pathItem);
                                for(String method : operations.keySet()){//对于每一个操作,创建一个请求
                                    if(operations.get(method)!=null && method!="delete"){//先不考虑删除操作，会影响资源的存在，因为资源之间的依赖导致请求无效
                                        io.swagger.v3.oas.models.Operation operation=operations.get(method);
                                        Map<String,String> headers=new HashMap<>();//请求头文件
                                        Map<String,Object> entity=new HashMap<>();// 请求体
                                        List<Object> arrayEntity=new ArrayList<>();// 请求体（数组形式
                                        String requestPath=pathKey;//请求路径

                                        List<Parameter> parameters= operation.getParameters();
                                        Map<String,String> queryParas=new HashMap<>();//查询参数
                                        Map<String,String> pathParas=new HashMap<>();//路径参数
                                        if(parameters!=null) {
                                            for (Parameter parameter : parameters) {
                                    /*//Swagger解析时，RefParameter会直接连接到对应属性，并生成对应的实例
                                    if(parameter.getClass().getName()=="RefParameter"){
                                        RefParameter refpara=(RefParameter)parameter;
                                        String ref=refpara.get$ref();
                                    }*/
                                                if(parameter.get$ref()!=null){
                                                    String[] refStrings=parameter.get$ref().split("/");
                                                    parameter=componetParas.get(refStrings[refStrings.length-1]);
                                                }
                                                if (parameter.getRequired()!=null && parameter.getRequired()) {//必需属性

                                                    String paraType = parameter.getSchema().getType();
                                                    String paraName = parameter.getName();
                                                    String paraValue = "";//填充后的值
                                                    String paraIn = parameter.getIn();

                                                    //生成属性值
                                                    Map<String, Example> paraExamples = parameter.getExamples();//获得说明文档中的示例值
                                                    String ppname;
                                                    if (paraIn .equals("path") ) {
                                                        ppname = StanfordNLP.removeBrace(pathKey.substring(0, pathKey.indexOf("{" + paraName + "}")));
                                                    } else {
                                                        ppname = StanfordNLP.removeBrace(pathKey);
                                                    }
                                                    ppname = StanfordNLP.removeSlash(ppname);//去除多余/和尾/
                                                    String paraMapValue = "";
                                                    if (pathParameterMap.containsKey(ppname)) {
                                                        //获取对应的路径属性散列表中的属性值,先只获取第一个
                                                        if (pathParameterMap.get(ppname).get(paraName).size() > 0) {
                                                            paraMapValue = pathParameterMap.get(ppname).get(paraName).get(0);
                                                        }

                                                    }
                                                    //生成属性值：优先级排序：说明文档提供的枚举值，路径属性散列表，类型默认值
                                                    if (paraExamples != null) {
                                                        for (String exkey : paraExamples.keySet()) {
                                                            paraValue = paraExamples.get(exkey).getValue().toString();
                                                            break;
                                                        }

                                                    } else if (paraMapValue != null && paraMapValue.length() != 0) {
                                                        paraValue = paraMapValue;
                                                    } else {
                                                        paraValue = getDefaultFromType(paraType, paraName).toString();
//                                                paraValue=getDefaultStringFromName(paraName).toString();
                                                    }

                                                    //根据属性位置给请求填充属性

                                                    if (paraIn .equals("path") ) {//路径属性
                                                        //requestPath = requestPath.replace("{" + paraName + "}", paraValue);
                                                        pathParas.put(paraName, paraValue);
                                                    } else if (paraIn .equals("query") ) {//查询属性
                                                        queryParas.put(paraName, paraValue);
                                                    } else if (paraIn .equals("header") ) {
                                                        headers.put(paraName, paraValue);
                                                    } else if (paraIn .equals("cookie") ) {
                                                        headers.put("cookie", paraValue);
                                                    }
                                                }
                                            }
                                        }
                                        RequestBody requestBody = operation.getRequestBody();
                                        if(requestBody!=null) {
                                            Content requestContent = requestBody.getContent();
                                            if (requestContent!=null && requestContent.get("application/json")!=null) {
                                                if(requestContent.get("application/json").getExamples()!=null){
                                                    entity.putAll(requestContent.get("application/json").getExamples());
                                                }else{
                                                    Schema schema = requestContent.get("application/json").getSchema();
                                                    try{
                                                        //尝试强转为子类composedschema
                                                        // 这里的schema列表可以进行遍历，先获得第一个schema
                                                        ComposedSchema composedSchema=(ComposedSchema)schema;
                                                        schema=composedSchema.getAllOf()!=null?composedSchema.getAllOf().get(0):schema;
                                                        schema=composedSchema.getAnyOf()!=null?composedSchema.getAnyOf().get(0):schema;
                                                        schema=composedSchema.getOneOf()!=null?composedSchema.getOneOf().get(0):schema;
                                                    }catch (ClassCastException e){

                                                    }
                                                    boolean isArray=false;
                                                    if(schema!=null && "array".equals(schema.getType())){
                                                        isArray=true;
                                                        schema=((ArraySchema) schema).getItems();
                                                    }
                                                    if(schema!=null && "object".equals(schema.getType()) && schema.getProperties()!=null){
                                                        entity = parseSchemaToEntity(result.getOpenAPI(),schema.getProperties());
                                                        if(isArray){
                                                            arrayEntity.add(entity);
                                                        }
                                                    }
                                                }

                                            }
                                        }
                                        /*if(queryParas.size()!=0){//拼接查询属性到url中
                                            String querPart="";
                                            for(String paraname:queryParas.keySet()){
                                                querPart+=paraname+"="+queryParas.get(paraname)+"&";
                                            }
                                            querPart="?"+querPart;
                                            querPart=querPart.substring(0,querPart.length()-1);
                                            requestPath+=querPart;
                                        }
                                        String url=serverURL+requestPath;*/
                                        //将消息体对象转化为字符串
                                        String entitystring="";
                                        if(arrayEntity.size()!=0){
                                            JSONArray jsonArray=JSONArray.fromObject(arrayEntity);
                                            entitystring=jsonArray.toString();
                                        }else{
                                            JSONObject jsonObject=JSONObject.fromObject(entity);
                                            entitystring = jsonObject.toString();
                                        }
                                        Request request=new Request(serverURL,pathKey,method,headers,pathParas,queryParas,entitystring);
                                        request.buildURL();
                                        dynamicValidateByURL(pathKey,request,false,false);
                                        //开始变异
                                        RequestGenerator requestGenerator = new RequestGenerator(request);
                                        //属性变异(路径属性或查询属性不为空时进行）
                                        if(!request.getQueryParameters().isEmpty() || !request.getPathParameters().isEmpty()) {
                                            //进行delete变异
                                            List<Request> fuzzingRequests = requestGenerator.paraFuzzingByRate("delete", 10, 80);
                                            for (Request re : fuzzingRequests) {
                                                dynamicValidateByURL(pathKey, re, false, false);
                                            }
                                            //进行type变异
                                            fuzzingRequests = requestGenerator.paraFuzzingByRate("type", 10, 80);
                                            for (Request re : fuzzingRequests) {
                                                dynamicValidateByURL(pathKey, re, false, false);
                                            }
                                            //进行format变异
                                            fuzzingRequests = requestGenerator.paraFuzzingByRate("format", 10, 80);
                                            for (Request re : fuzzingRequests) {
                                                dynamicValidateByURL(pathKey, re, false, false);
                                            }
                                        }
                                        //头文件变异，只进行delete变异
                                        if(!request.getHeader().isEmpty()){
                                            //进行delete变异
                                            List<Request> fuzzingRequests = requestGenerator.headerFuzzingByRate("delete", 10, 80);
                                            for (Request re : fuzzingRequests) {
                                                dynamicValidateByURL(pathKey, re, false, false);
                                            }
                                        }
                                        //消息体不为空的话，消息体变异
                                        if(entitystring!=""){
                                            List<Request> fuzzingRequests=requestGenerator.bodyFuzzing(entitystring,"delete",10);
                                            for (Request re : fuzzingRequests) {
                                                dynamicValidateByURL(pathKey, re, false, false);
                                            }
                                        }
                                /*RandomRequestGenerator rrg=new RandomRequestGenerator(request);
                                List<Request> randomRequests=rrg.requestGenerate();*/
                                    }
                                }

                                //遍历后处理
                                //将该结点加入已遍历过的结点集中
                                visitedNodes.add(pathKey);
                                //处理from边集
                                for(Iterator<Map.Entry<String, Set<String>>> it=edgesMap.entrySet().iterator();it.hasNext();){
                                    Map.Entry<String,Set<String>> fromEdge= it.next();
                                    //删除当前结点的依赖
                                    Set<String> toPaths=fromEdge.getValue();
                                    if(toPaths.contains(pathKey)){
                                        toPaths.remove(pathKey);
                                    }
                                    //如果from结点已经没有to结点，删除
                                    if(toPaths.size()==0){
                                        it.remove();
                                    }
                                }
                                /*for(Map.Entry<String,Set<String>> fromEdge:edgesMap.entrySet()){
                                    //删除当前结点的依赖
                                    Set<String> toPaths=fromEdge.getValue();
                                    if(toPaths.contains(pathKey)){
                                        toPaths.remove(pathKey);
                                    }
                                    //如果from结点已经没有to结点，删除
                                    if(toPaths.size()==0){
                                        edgesMap.remove(fromEdge.getKey());
                                    }

                                }*/
                            }
                        }
                    }





                }
            }
        }
        consistencyDetect();
    }



    /**
     * OAS3将Schema（可能存在嵌套）解析为Map，最终转化为json格式消息体
     * @param openapi(用来获取component中的schema),properties
     * @return
     */
    private Map<String, Object> parseSchemaToEntity(OpenAPI openapi,Map<String, Schema> properties) {
        Map<String, Object> result=new HashMap<>();
        for(String schemaKey:properties.keySet()){
            Schema schema=properties.get(schemaKey);
            if(schema.get$ref()!=null){
                String[] refStrings=schema.get$ref().split("/");
                schema=openapi.getComponents().getSchemas().get(refStrings[refStrings.length-1]);
            }
            if(schema.getType()==null){
                //TODO
                result.put(schemaKey,"null");
            }
            else if(schema.getType().equals("object") && schema.getProperties()!=null){
                result.put(schemaKey,parseSchemaToEntity(openapi,schema.getProperties()));
            }else{
                result.put(schemaKey,getDefaultFromType(schema.getType(),schemaKey));
            }

        }
        return result;
    }

    /**
     * 根据名称获得默认值(String类型）
     * @param paraName
     * @return
     */
    private String getDefaultStringFromName(String paraName) {
        if(paraName.toLowerCase().contains("id")){
            return uuid;
        }
        if(paraName.toLowerCase().contains("date") || paraName.toLowerCase().contains("time")
                || paraName.toLowerCase().contains("last") || paraName.toLowerCase().contains("first")){
            return date;
        }
        if(paraName.toLowerCase().contains("url") || paraName.toLowerCase().contains("link")){
            return url;
        }
        if(paraName.toLowerCase().contains("key")){
            return key;
        }
        if(paraName.toLowerCase().contains("token")){
            return token;
        }
        if(paraName.toLowerCase().contains("no")){
            return no;
        }
        if(paraName.toLowerCase().contains("email")){
            return email;
        }
        if(paraName.toLowerCase().contains("owner") || paraName.toLowerCase().contains("user")){
            return owner;
        }
        if(paraName.toLowerCase().contains("lan") || paraName.toLowerCase().contains("language")){
            return language;
        }
        if(paraName.toLowerCase().contains("org") || paraName.toLowerCase().contains("organization")){
            return org;
        }
        return  "rester";
    }

    /**
     * 一致性检测
     */
    private void consistencyDetect() {
        System.out.println("Consistency detection:");
        if(this.hasStrongCache!=this.hasStrongCacheStatic){
            System.out.println("strong cache has inconsistency:RAD "+this.hasStrongCacheStatic+"| real response "+this.hasStrongCache);
        }
        if(this.hasNegCacheStatic!=this.hasNegCache){
            System.out.println("Negotiation cache has inconsistency:RAD "+this.hasNegCacheStatic+"| real response "+this.hasNegCache);
        }
        if(this.hasResponseContentType!=this.hasResponseContentTypeStatic){
            System.out.println("content-type has inconsistency:RAD "+this.hasResponseContentTypeStatic+"| real response "+this.hasResponseContentType);
        }
        if(this.hateoasStatic!=this.hateoas){
            System.out.println("HATEOAS has inconsistency:RAD "+this.hateoasStatic+"| real response "+this.hateoas);
        }
        System.out.println("Consistency detection end!");
    }

    /**
     * OAS2解析properties成为Map对象，最终这个Map会转成json作为请求消息体
     * @param properties objectProperty存在嵌套，Array、Map
     * @return
     */
    private Map<String, Object> parsePropertiesToEntity(Map<String, Property> properties) {
        Map<String, Object> result=new HashMap<>();
        for(String proName:properties.keySet()){
            Property pro=properties.get(proName);
            if(pro.getExample()!=null){//检查说明中有无例子，有的话直接使用例子
                result.put(proName,pro.getExample());
            }else{
                //根据property的类别进行值的生成
                if(pro.getType()=="object"/*pro.getClass().getName()=="ObjectProperty"*/){//只有ObjectProperty存在嵌套
                    ObjectProperty obPro=(ObjectProperty)pro;
                    result.put(proName,parsePropertiesToEntity(obPro.getProperties()));
                }else if(pro.getType()=="array"){
                    ArrayProperty arrPro=(ArrayProperty)pro;
                    String itemType=arrPro.getItems().getType();
                    List<Object> items=new ArrayList<>();
                    items.add(getDefaultFromType(itemType,proName));//返回基本类型的默认值
                    result.put(proName,items);

                }else if(pro.getType()=="map"){
                    MapProperty mapPro=(MapProperty)pro;
                    String proType=mapPro.getAdditionalProperties().getType();
                    Map<String,Object> pros=new HashMap<>();
                    pros.put(proType,getDefaultFromType(proType,proName));
                    result.put(proName,pros);
                }else{
                    result.put(proName,getDefaultFromType(pro.getType(),proName));
                }
            }

        }
        return result;
    }

    /**
     * 根据基本类型返回默认值
     * @param itemType
     * @return
     */
    private Object getDefaultFromType(String itemType,String paraName) {
        if(itemType=="string"){
            return getDefaultStringFromName(paraName);
        }else if(itemType=="integer"){
            return 0;
        }else if(itemType=="boolean"){
            return true;
        }else{
            return "default";
        }
    }

    /**
     * 通过API网页检测
     * @param html
     */
    public void validateByHengSheng(String html){
        RESTModel model=new HSModel(html);
        validateByModel(model);

    }

    private void validateByModel(RESTModel model)  {

        this.name=model.getName();
        //基本信息获取
        basicInfoGet(model);
        //路径检测
        Map<String,Object> paths=model.getPaths();
        if(paths.size()==0) return ;
        pathEvaluate(paths.keySet(),model);
        //域名检测
        List<String> servers=model.getBaseURLs();
        serverEvaluate(servers.get(0));

        //请求参数检测
        List<ParameterRESTer> reqParameters=new ArrayList<>();//请求参数
        List<ParameterRESTer> resParameters=new ArrayList<>();//响应参数
        for(String pathname:paths.keySet()){
            PathRESTer pathTemp= (PathRESTer) paths.get(pathname);
            reqParameters.addAll(pathTemp.getParameters());//收集路径级别参数
            List<OperationRESTer> operations=pathTemp.getOperations();
            for(OperationRESTer operation:operations){
                reqParameters.addAll(operation.getParameters());//收集操作级别参数
                List<ResponseRESTer> responses=operation.getResponses();
                for(ResponseRESTer response:responses){
                    resParameters.addAll(response.getHeaders());//收集响应参数
                }
            }

        }
        for(ParameterRESTer parameterRester:reqParameters){
            String paraName=parameterRester.getName();
            paraName=paraName.toLowerCase();
            //网页介绍中的属性没有位置（in)
            //if( parameter.getIn().equals("query")){//查询属性
            if(isPagePara(paraName)){//功能性属性
                this.querypara.add(paraName);
                setHasPagePara(true);
                System.out.println(paraName+" is page parameter. ");
            }/*else if(paraName.contains("version")){//版本信息
                this.versionInQueryPara=true;
                System.out.println("Query-parameter shouldn't has version parameter: "+paraName);
            }*/
            //}else if(parameter.getIn().equals("header")){//头文件属性
            else if(paraName.contains("version")){
                this.versionInHead=true;
            }else if(paraName.contains("key") ){
                this.hasKey=true;
            } else if(paraName.contains("token")){
                this.hasToken=true;
            } else  if(paraName.contains("authorization") ){
                this.hasAuthorization=true;

            }else if(paraName.equals("accept")){
                this.hasAccept=true;
            }
            //}
        }

        //请求参数检测
        for(ParameterRESTer resParameter:resParameters){
            String headerName=resParameter.getName();
            headerName=headerName.toLowerCase();
            if(headerName.equals("cache-control") ){
                hasCacheControlStatic=true;
            }else if( headerName.equals("expires")){
                hasExpiresStatic=true;
            }else if( headerName.equals("date") ){
                hasDateStatic=true;
            }else if(headerName.equals("etag") ){
                this.hasEtagStatic=true;
            } else if(headerName.equals("last-modified")){
                this.hasLastModifiedStatic=true;
            }else if(headerName.equals("content-type") ){
                this.hasResponseContentTypeStatic=true;
            }
        }
        this.hasStrongCacheStatic=this.hasCacheControlStatic || this.hasDateStatic || this.hasExpiresStatic;
        this.hasNegCacheStatic=this.hasEtagStatic || this.hasLastModifiedStatic;

        this.score=scoreCalculate();
        //填写输出Json
        setValidateResult();
    }

    /**
     * 通过路径（端点）网页检测
     * @param html
     * @throws FileNotFoundException
     * @throws JWNLException
     */
    public void validateByHengShengEndpoint(String html) throws FileNotFoundException, JWNLException {
        JWNLwordnet jwnLwordnet=new JWNLwordnet();
        String path="";
        String server="";
        String url="";
        String operation="";
        String supportType="";
        List<String> reqParameters=new ArrayList<>();//请求参数名
        List<String> resParameters=new ArrayList<>();//响应参数名
        Document document = Jsoup.parse(html);

        //像js一样，通过class 获取列表下的所有博客
        Elements apiTables = document.getElementsByClass("api_table");
        //循环处理每篇博客

        for(int i=0;i<apiTables.size();i++){
            Element apiTable=apiTables.get(i);
            switch (i){
                case 0:
                    Element shaxiangTd=apiTable.getElementsByTag("td").get(1);
                    server=shaxiangTd.text();
                    break;
                case 1:
                    url=apiTable.text();
                    path=url.substring(server.length());
                    break;
                case 2:
                    break;
                case 3:
                    operation=apiTable.text();
                    break;
                case 4:
                    supportType=apiTable.text();
                    break;
                case 5:
                    Elements trs=apiTable.getElementsByTag("tr");
                    for(int j=1;j<trs.size();j++){
                        Element paraname=trs.get(j).getElementsByTag("td").get(0);
                        reqParameters.add(paraname.text());
                    }
                    break;
                case 6:
                    Elements trsres=apiTable.getElementsByTag("tr");
                    for(int j=1;j<trsres.size();j++){
                        Element paraname=trsres.get(j).getElementsByTag("td").get(0);
                        resParameters.add(paraname.text());
                    }
                    break;
            }
        }
        //路径检测
        Map<String,Object> pathResult=new HashMap<>();
        String p=path;
        //evaluateToScore()
        if(p.contains("_")){
            //System.out.println(p+" has _");
            //this.score=this.score-20>0?this.score-20:0;
            pathResult.put("no_",false);
        }else {
            this.pathEvaData[0]++;//Integer是Object子类，是对象，可以为null。int是基本数据类型，必须初始化，默认为0
            pathResult.put("no_",true);
        }

        if(p!=p.toLowerCase()){
            //System.out.println(p+"need to be lowercase");
            //this.score=this.score-20>0?this.score-20:0;
            pathResult.put("lowercase",false);
        }else {
            this.pathEvaData[1]++;
            pathResult.put("lowercase",true);
        }

        Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
        Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
        Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
        Matcher m2=pattern2.matcher(p);
        if(m2.find()){
            System.out.println("version shouldn't in paths "+p);
            //this.score=this.score-5>0?this.score-5:0;
            String version=m2.group();
            this.versionInPath=true;
            if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                this.semanticVersion=true;
            }
            pathResult.put("noVersion",false);
        }else {
            this.pathEvaData[2]++;
            pathResult.put("noVersion",true);
        }
        if(p.toLowerCase().contains("api")){
            System.out.println("api shouldn't in path "+p);
            //this.score=this.score-10>0?this.score-10:0;
            pathResult.put("noapi",false);
        }else {
            this.pathEvaData[3]++;
            pathResult.put("noapi",true);
        }

        //this.pathlist.add(p);
        Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
        Matcher m = pp.matcher(p);
        String pathclear = "";//去除属性{}之后的路径
        int endtemp=0;
        while(m.find()){
            pathclear+=p.substring(endtemp,m.start());
            endtemp=m.end();
        }
        pathclear+=p.substring(endtemp);
        pathclear=pathclear.toLowerCase();
        //String crudnames[]=CRUDNAMES;
        String crudnames[]=ConfigManager.getInstance().getValue("CRUDNAMES").split(",",-1);

        String dellistString=ConfigManager.getInstance().getValue("DELLIST");
        String str1[] = dellistString.split(";",-1);
        String delList[][]=new String[str1.length][];
        for(int i = 0;i < str1.length;i++) {

            String str2[] = str1[i].split(",");
            delList[i] = str2;
        }
        //String delList[][]=DELLIST;
        boolean isCrudy = false;
        List<String> verblist=new ArrayList<>();
        for(int i=0; i< crudnames.length; i++){
            // notice it should start with the CRUD name
            String temp=fileHandle.delListFromString(pathclear,delList[i]);
            if (temp.contains(crudnames[i])) {
                isCrudy = true;
                verblist.add(crudnames[i]);
                if(crudnames[i]!="create" && crudnames[i]!="add" && crudnames[i]!="post" && crudnames[i]!="new" && crudnames[i]!="push" ){
                    this.hasWrongPost=true;
                }
                List<String> pathOP=new ArrayList<>();
                pathOP.add(p);
                pathOP.add(crudnames[i]);
                pathOP.add(operation);
                CRUDPathOperations.add(pathOP);
                break;
            }
        }
        this.CRUDlist.addAll(verblist);
        pathResult.put("CRUDlist",verblist);
        if(isCrudy){
            System.out.println("CRUD shouldn't in path "+p);
            //this.score=this.score-20>0?this.score-20:0;
            pathResult.put("noCRUD",false);

        }else{
            this.pathEvaData[4]++;
            pathResult.put("noCRUD",true);
        }
        //层级之间的语义上下文关系
        List<String> splitPaths;
        String pathText=pathclear.replace("/"," ");
        splitPaths=StanfordNLP.getlemma(pathText);//词形还原
        if(splitPaths.size()>=2){
                /*WordNet wordNet=new WordNet();
                wordNet.hasRelation(splitPaths);//检测是否具有上下文关系*/
            if (jwnLwordnet.hasRelation(splitPaths)){
                this.hasContextedRelation=true;
            }

        }

        //文件扩展名不应该包含在API的URL命名中
        //String suffix[]=SUFFIX_NAMES;
        String suffix[]=ConfigManager.getInstance().getValue("SUFFIX_NAMES").split(",",-1);
        boolean isSuffix = false;
        List<String> slist=new ArrayList<>();
        for(int i=0; i< suffix.length; i++){
            if (p.toLowerCase().indexOf(suffix[i]) >=0) {
                isSuffix = true;
                slist.add(suffix[i]);

                break;
            }
        }
        this.suffixlist.addAll(slist);
        pathResult.put("suffixList",slist);
        if(isSuffix){
            System.out.println("suffix shouldn't in path "+p);
            //this.score=this.score-20>0?this.score-20:0;
            pathResult.put("noSuffix",false);
        }else {
            this.pathEvaData[5]++;
            pathResult.put("noSuffix",true);
        }



        //使用正斜杠分隔符“/”来表示一个层次关系，尾斜杠不包含在URL中
        int hierarchyNum=0;
        if(p.endsWith("/") && p.length()>1){
            //System.out.println(p+" :尾斜杠不包含在URL中");
            //this.score=this.score-20>0?this.score-20:0;
            hierarchyNum=substringCount(p,"/")-1;
            this.hierarchies.add(Integer.toString(hierarchyNum));
            this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
            this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数
            pathResult.put("noend/",false);

        }else{
            pathResult.put("noend/",true);
            this.pathEvaData[6]++;
            //建议嵌套深度一般不超过3层
            hierarchyNum=substringCount(p,"/");
            this.hierarchies.add(Integer.toString(hierarchyNum));
            this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
            this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数

        }
        pathResult.put("hierarchies",hierarchyNum);
        pathDetail.put(p,pathResult);

        //域名检测
        serverEvaluate(server);

        //请求参数检测
        for(String paraName:reqParameters){
            paraName=paraName.toLowerCase();
            //网页介绍中的属性没有位置（in)
            //if( parameter.getIn().equals("query")){//查询属性
                if(isPagePara(paraName)){//功能性属性
                    this.querypara.add(paraName);
                    setHasPagePara(true);
                    System.out.println(paraName+" is page parameter. ");
                }else if(paraName.contains("version")){//版本信息
                    this.versionInQueryPara=true;
                    System.out.println("Query-parameter shouldn't has version parameter: "+paraName);
                }
            //}else if(parameter.getIn().equals("header")){//头文件属性
                else if(paraName.contains("version")){
                    this.versionInHead=true;
                }else if(paraName.contains("key") ){
                    this.hasKey=true;
                } else if(paraName.contains("token")){
                    this.hasToken=true;
                } else  if(paraName.contains("authorization") ){
                    this.hasAuthorization=true;

                }else if(paraName.equals("accept")){
                    this.hasAccept=true;
                }
            //}
        }

        for(String headerName:resParameters){
            headerName=headerName.toLowerCase();
            if(headerName.equals("cache-control") ){
                hasCacheControlStatic=true;
            }else if( headerName.equals("expires")){
                hasExpiresStatic=true;
            }else if( headerName.equals("date") ){
                hasDateStatic=true;

            }else if(headerName.equals("etag") ){
                this.hasEtagStatic=true;
            } else if(headerName.equals("last-modified")){
                this.hasLastModifiedStatic=true;

            }else if(headerName.equals("content-type") ){
                this.hasResponseContentTypeStatic=true;
            }
        }
        this.score=scoreCalculate();

    }
    public ValidationResponse debugByContent(RequestContext request, String content)  {

        ValidationResponse output = new ValidationResponse();

        // convert to a JsonNode

        JsonNode spec = readNode(content); //解析json/yaml格式，生成树结构
        if (spec == null) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage("Unable to read content.  It may be invalid JSON or YAML");
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }

        boolean isVersion2 = false;

        // get the version, return deprecated if version 1.x
        String version = getVersion(spec);
        validateResult.put("openapiVersion",version);

        if (version != null && (version.startsWith("\"1") || version.startsWith("1"))) {
            ProcessingMessage pm = new ProcessingMessage();
            pm.setLogLevel(LogLevel.ERROR);
            pm.setMessage(INVALID_VERSION);
            output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            return output;
        }
        else if (version != null && (version.startsWith("\"2") || version.startsWith("2"))) {
            isVersion2 = true;
            SwaggerDeserializationResult result = null;
            try {
                result = readSwagger(content);  //根据content构建swagger model
            } catch (Exception e) {
                LOGGER.debug("can't read Swagger contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse Swagger: " + e.getMessage());
                output.addValidationMessage(new SchemaValidationError(pm.asJson()));
                return output;
            }
            if (result != null) {
                for (String message : result.getMessages()) {
                    output.addMessage(message);
                }
                this.name=result.getSwagger().getInfo().getTitle();
                //validateResult.put("name",this.name);

                //路径（命名）检测
                Set paths = result.getSwagger().getPaths().keySet();
                pathlist= new ArrayList<>(paths);
                pathEvaluate(paths,result);



                //安全解决方案
                Map<String, SecuritySchemeDefinition> securityDefinitions = result.getSwagger().getSecurityDefinitions()==null?null:result.getSwagger().getSecurityDefinitions();
                if(securityDefinitions!=null){
                    for (String key : securityDefinitions.keySet()) {
                        this.security.add(securityDefinitions.get(key).getType());
                        evaluations.put("securityType",securityDefinitions.get(key).getType());
                        System.out.println("securityType ：" + securityDefinitions.get(key).getType());
                    }
                }

                //基本信息统计
                basicInfoGet(result);
                //域名检测
                String serverurl=result.getSwagger().getHost()+result.getSwagger().getBasePath();
                serverEvaluate(serverurl);
                //属性研究,swagger解析出属性:全局属性，路径级别属性，操作级别属性（path-> operation -> parameter）
                List<io.swagger.models.parameters.Parameter> parameters= new ArrayList<>();
                Map<String, io.swagger.models.parameters.Parameter> parametersInSwagger = result.getSwagger().getParameters();//提取全局属性,加入到属性列表中
                if(parametersInSwagger!=null){
                    for(io.swagger.models.parameters.Parameter parameter:parametersInSwagger.values()){
                        parameters.add(parameter);
                    }
                }
                int opCount=0;
                int x2s=0,x3s=0,x4s=0,x5s=0;
                for(String pathName : result.getSwagger().getPaths().keySet()){
                    Path path = result.getSwagger().getPath(pathName);

                    if(path.getParameters()!=null) {
                        parameters.addAll(path.getParameters());//提取路径级别属性，加入属性列表中
                        //构建路径必须属性散列表
                        for(io.swagger.models.parameters.Parameter p:path.getParameters()){
                            //非必须属性也加入路径属性列表中
//                            if(p.getRequired()==true){
                                String pIn=p.getIn();//属性位置
                                String pName=p.getName();//属性名称
                                buildPathParameterMap(pathName,pName,pIn);
//                            }
                        }
                    }
                    //提取操作级别属性
                    List<io.swagger.models.Operation> operations=getAllOperationsInAPath(path);

                    for(io.swagger.models.Operation operation : operations){
                        boolean x2=false;
                        boolean x3=false;
                        boolean x4=false;
                        boolean x5=false;
                        //统计状态码使用情况
                        opCount++;
                        Map<String, io.swagger.models.Response> responses=operation.getResponses();
                        if(responses!=null){
                            for(String s:responses.keySet()){
                                if(s.startsWith("2")){
                                    x2=true;
                                }else if(s.startsWith("3")){
                                    x3=true;
                                }else if(s.startsWith("4")){
                                    x4=true;
                                }else if(s.startsWith("5")){
                                    x5=true;
                                }else if(s.charAt(0)>'5'){
                                    this.hasWrongStatus=true;

                                }
                                if(status.containsKey(s)){
                                    status.put(s,status.get(s)+1);
                                }else{
                                    status.put(s,1);
                                }
                                io.swagger.models.Response response =responses.get(s);
                                if(response.getHeaders()!=null){
                                    for(String headerName:response.getHeaders().keySet()){
                                        headerName=headerName.toLowerCase();
                                        if(headerName.equals("cache-control") ){
                                            this.hasCacheControlStatic=true;
                                        }else if( headerName.equals("expires")){
                                            this.hasExpiresStatic=true;
                                        }else if( headerName.equals("date") ){
                                            this.hasDateStatic=true;

                                        }else if(headerName.equals("etag") ){
                                            this.hasEtagStatic=true;
                                        } else if(headerName.equals("last-modified")){
                                            this.hasLastModifiedStatic=true;

                                        }else if(headerName.equals("content-type") ){
                                            this.hasResponseContentTypeStatic=true;
                                        }
                                    }
                                }
                                Model responseSchema = response.getResponseSchema();
                                //检测是否实现hateoas原则
                                if(responseSchema!=null){
                                    Map<String, Property> properties = responseSchema.getProperties();
                                    if(properties!=null){
                                        for(String proname:properties.keySet()){
                                            if(proname.toLowerCase().contains("link")){
                                                this.hateoasStatic=true;
                                            }
                                        }
                                    }

                                }
                            }
                        }
                        x2s+=x2?1:0;
                        x3s+=x3?1:0;
                        x4s+=x4?1:0;
                        x5s+=x5?1:0;


                        //加入操作级别属性到属性列表中
                        if(operation.getParameters()!=null) {
                            parameters.addAll(operation.getParameters());

                            //构建路径必须属性散列表
                            for (io.swagger.models.parameters.Parameter p : operation.getParameters()) {
                                //非必需属性也构建的到属性列表中
//                                if (p.getRequired() == true) {
                                    String pIn = p.getIn();//属性位置
                                    String pName = p.getName();//属性名称
                                    buildPathParameterMap(pathName, pName, pIn);
//                                }
                            }
                        }
                    }
                }
                this.hasStrongCacheStatic=hasCacheControlStatic || hasDateStatic || hasExpiresStatic;
                this.hasNegCacheStatic=this.hasEtagStatic || hasLastModifiedStatic;

                //status.put("opcount",opCount);
                statusUsage= new int[]{opCount, x2s, x3s, x4s, x5s};

                if(parameters.size()!=0){
                    for(io.swagger.models.parameters.Parameter parameter:parameters){
                        if(parameter instanceof BodyParameter){

                        }
                        String paraName=parameter.getName().toLowerCase();
                        if(parameter.getIn().equals("query")){//查询属性
                            if(isPagePara(paraName)){//判断查询属性中是否有功能性属性
                                this.querypara.add(paraName);
                                setHasPagePara(true);
                                System.out.println(paraName+" is page parameter. ");
                            }else if(paraName.contains("version")){//版本信息
                                this.versionInQueryPara=true;
                                System.out.println("Query-parameter shouldn't has version parameter: "+paraName);
                            }
                        }else if(parameter.getIn().equals("header")){
                            if(parameter.getName().contains("version")){
                                this.versionInHead=true;
                            }else if(paraName.contains("key") ){
                                this.hasKey=true;
                            } else if(paraName.contains("token")){
                                this.hasToken=true;
                            } else  if(paraName.contains("authorization") ){
                                this.hasAuthorization=true;

                            }else if(paraName.equals("accept")){
                                this.hasAccept=true;
                            }
                        }
                    }
                }
                this.securityInHeadPara=this.hasKey || this.hasToken || this.hasAuthorization;
                evaluations.put("hasPageParameter",String.valueOf(isHasPagePara()));


                //类别信息获取
                setCategory(result);
                this.score=scoreCalculate();
                //填写输出Json
                setValidateResult();


            }//if result!=null
        }
        else if (version == null || (version.startsWith("\"3") || version.startsWith("3"))) {
            SwaggerParseResult result = null;
            try {
                result = readOpenApi(content);
            } catch (Exception e) {
                LOGGER.debug("can't read OpenAPI contents", e);

                ProcessingMessage pm = new ProcessingMessage();
                pm.setLogLevel(LogLevel.ERROR);
                pm.setMessage("unable to parse OpenAPI: " + e.getMessage());
                output.addValidationMessage(new SchemaValidationError(pm.asJson()));
                return output;
            }
            if (result != null) {


                //类别信息获取
                setCategory(result);
                this.name=result.getOpenAPI().getInfo().getTitle();
                //validateResult.put("name",this.name);
                for (String message : result.getMessages()) {
                    output.addMessage(message);
                    System.out.println(message);
                }

                //基本信息获取
                basicInfoGet(result);

                //路径命名验证
                Set paths = result.getOpenAPI().getPaths().keySet();
                pathlist= new ArrayList<>(paths);
                pathEvaluate(paths,result);

                //资源依赖分析：依赖图构建
                OpenAPIDeserializer deserializer = new OpenAPIDeserializer();
                Components component = result.getOpenAPI().getComponents();
                //构建点集
                for(Map.Entry<String,PathItem> entry:result.getOpenAPI().getPaths().entrySet()) {
                    String pathName=entry.getKey();
                    PathItem pathItem=entry.getValue();
                    GraphNode<PathItem> graphNode=new GraphNode(pathName,pathItem);
                    dependenceGraph.addNode(pathName,graphNode);
                }
                //构建边集
                //for(Map.Entry<String,PathItem> entry:result.getOpenAPI().getPaths().entrySet()){
                for(Map.Entry<String,GraphNode> entry:dependenceGraph.getNodes().entrySet()){
                    String pathName=entry.getKey();
                    PathItem pathItem= (PathItem) entry.getValue().getPathItem();
                    boolean hasEdge=false;// 是否添加了以该点为from的边
                    //按照层级查找依赖关系，权重为“1”
                    String[] pathHies=pathName.split("/");
                    for (int i = pathHies.length-1; i >=0; i--) {
                        int index=pathName.indexOf(pathHies[i]);
                        if(index>1){
                            String father=pathName.substring(0,index-1);
                            if(dependenceGraph.containsNode(father)){
                                dependenceGraph.addEdge(pathName,father,"1");
                                hasEdge=true;
                                break;
                            }
                        }
                    }
                    //  粗粒度剪枝，粒度过粗，不使用该剪枝策略，已经添加的边，就跳过下面过程
                    //if(hasEdge) continue;
                    //按照属性查找依赖关系，权重为“2”
                    // 路径的输入属性（路径属性）有无出现在别的路径的输出属性（消息体属性）中
                    List<io.swagger.v3.oas.models.Operation> operations= deserializer.getAllOperationsInAPath(pathItem);//获取所有操作
                    //获得资源名
                    String resourceName="";// 被测路径的资源名
                    if(pathName.contains("{")){
                        String pathResource=StanfordNLP.removeSlash(StanfordNLP.removeBrace(pathName.substring(0,pathName.indexOf("{"))));
                        String[] res=pathResource.split("/");
                        resourceName=res[res.length-1];
                    }else{
                        String[] res=pathName.split("/");
                        if(res.length>0)
                            resourceName=res[res.length-1];
                    }
                    //整理被测路径的输入属性，扩展名称：path+para
                    Set<String> inputParas=new HashSet<>();
                    for(Operation operation:operations){
                        List<Parameter> paras=operation.getParameters();
                        if(paras!=null){
                            for(Parameter para:paras){
                                if(para.get$ref()!=null){
                                    String[] ref=para.get$ref().split("/");
                                    String paraName=ref[ref.length-1];
                                    para=component.getParameters().get(paraName);
                                }
                                inputParas.add(resourceName+"_"+para.getName());
                            }
                        }
                    }
                    //如果有输入属性，可能存在属性依赖，开始查找
                    if(inputParas.size()>0){
                        // 遍历其他路径的输出属性（响应体属性）中是否包含该路径的输入属性
                        //for(Map.Entry<String,PathItem> otherPath:result.getOpenAPI().getPaths().entrySet()){
                        for(Map.Entry<String,GraphNode> otherPath:dependenceGraph.getNodes().entrySet()){
                            String otherPathName=otherPath.getKey();
                            PathItem otherPathItem=(PathItem) otherPath.getValue().getPathItem();
                            //如果otherPathName为当前结点的依赖结点（包含间接依赖，即祖孙关系），剪枝
                            if(dependenceGraph.getEdgesMap().containsKey(pathName) && dependenceGraph.getEdgesMap().get(pathName).contains(otherPathName)){
                                continue;
                            }
                            if(!otherPathName.equals(pathName)){
                                //遍历输出属性：操作-》响应->content
                                List<Operation> otherPathOps= deserializer.getAllOperationsInAPath(otherPathItem);
                                for(Operation op:otherPathOps){
                                    // 存在响应体 ApiResponse -> Content -> MediaType->Schema
                                    if(op.getResponses()!=null){
                                        for(ApiResponse res:op.getResponses().values()){
                                            // 如果引用响应不为null，取引用响应
                                            if(res.get$ref()!=null){
                                                String[] ref=res.get$ref().split("/");
                                                String responseName=ref[ref.length-1];
                                                res=component.getResponses().get(responseName);
                                            }
                                            if(res.getContent()!=null){
                                                for(MediaType mediaType:res.getContent().values()){
                                                    if(mediaType.getSchema()!=null){
                                                        Schema schema = mediaType.getSchema();
                                                        // 如果引用schema不为null，使用引用schema
                                                        if(schema.get$ref()!=null){
                                                            String[] sch=schema.get$ref().split("/");
                                                            schema=component.getSchemas().get(sch[sch.length-1]);
                                                        }
                                                        if(schema.getProperties()!=null){
                                                            for(Object proName:schema.getProperties().keySet()){
                                                                if(inputParas.contains((String)proName)){
                                                                    /*if(!dependenceGraph.containsNode(otherPathName)){
                                                                        dependenceGraph.addNode(otherPathName,otherPathItem);
                                                                    }*/
                                                                    // 匹配到了，添加一条边（种类为2）
                                                                    dependenceGraph.addEdge(pathName,otherPathName,"2");
                                                                    hasEdge=true;
                                                                    break;
                                                                }
                                                            }
                                                            if(hasEdge) break;
                                                        }

                                                    }
                                                }
                                                if(hasEdge) break;
                                            }
                                        }
                                        if(hasEdge) break;
                                    }
                                }
                                if(hasEdge) break;
                            }
                        }
                    }


                }
                //构建子图
                dependenceGraph.buildSubG();
                Map subG=dependenceGraph.getSubGs();

                //System.out.println(result.getOpenAPI().getSecurity());
                //获取API security方案类型（apiKey，OAuth，http等）

                if (component!=null){
                    Map<String, SecurityScheme> securitySchemes = component.getSecuritySchemes();
                    if(securitySchemes!=null){
                        for (String key : securitySchemes.keySet()) {
                            this.security.add(securitySchemes.get(key).getType().toString());
                            evaluations.put("securityType",securitySchemes.get(key).getType().toString());
                            System.out.println("securityType ：" + securitySchemes.get(key).getType().toString());
                        }
                    }else {
                        evaluations.put("securityType","null");
                    }

                }else{
                    evaluations.put("securityType","null");
                }
                //validateResult.put("securityList",getSecurity());

                //域名检测
                List<Server> servers=result.getOpenAPI().getServers();
                if(servers!=null){
                    for(Server server:servers){
                        String serverurl=server.getUrl();
                        serverEvaluate(serverurl);

                    }
                }
                //validateResult.put("apiInServer",this.apiInServer);

                //属性研究
                //openAPI完全按照说明文档进行解析，大部分属性信息在路径中
                List<Parameter> parameters=new ArrayList<>();
                Map<String, Parameter> parametersInComponent =null;
                if (component!=null) {
                    parametersInComponent = result.getOpenAPI().getComponents().getParameters();//全局属性
                    if (parametersInComponent != null) {
                        for (Parameter parameter : parametersInComponent.values()) {
                            parameters.add(parameter);//全局属性加入属性列表
                        }
                    }
                }

                int opCount=0;
                int x2s=0,x3s=0,x4s=0,x5s=0;
                for(String pathName : result.getOpenAPI().getPaths().keySet()){
                    //path-》operation-》parameters
                    if(result.getOpenAPI().getPaths().get(pathName).getParameters()!=null){
                        parameters.addAll(result.getOpenAPI().getPaths().get(pathName).getParameters());//路径级别属性加入属性列表

                        //构建路径必须属性散列表
                        for(Parameter p:result.getOpenAPI().getPaths().get(pathName).getParameters()){
                            if(p.get$ref()!=null){
                                String[] refStrings=p.get$ref().split("/");
                                p=parametersInComponent.get(refStrings[refStrings.length-1]);
                            }
                            //非必须属性也加入路径属性哈希表中
//                            if(p.getRequired()!=null && p.getRequired()==true){
                                String pIn=p.getIn();//属性位置
                                String pName=p.getName();//属性名称
                                buildPathParameterMap(pathName,pName,pIn);
//                            }
                        }
                    }

//                    OpenAPIDeserializer deserializer = new OpenAPIDeserializer();
                    List<io.swagger.v3.oas.models.Operation> operationsInAPath = deserializer.getAllOperationsInAPath(result.getOpenAPI().getPaths().get(pathName));//获取所有操作
                    this.endpointNum+=operationsInAPath.size();//统计端点数

                    for(io.swagger.v3.oas.models.Operation operation:operationsInAPath){
                        boolean x2=false;
                        boolean x3=false;
                        boolean x4=false;
                        boolean x5=false;
                        opCount++;
                        //操作级别属性加入属性列表
                        if(operation.getParameters()!=null){
                            parameters.addAll(operation.getParameters());
                            //构建路径必须属性散列表
                            for(Parameter p:operation.getParameters()){
                                if(p.get$ref()!=null){
                                    String[] refStrings=p.get$ref().split("/");
                                    p=parametersInComponent.get(refStrings[refStrings.length-1]);
                                }
                                //非必须属性也加入路径属性哈希表中
//                                if(p.getRequired()!=null && p.getRequired()==true){
                                    String pIn=p.getIn();//属性位置
                                    String pName=p.getName();//属性名称
                                    buildPathParameterMap(pathName,pName,pIn);
//                                }
                            }
                        }


                        if(operation.getResponses()!=null){
                            for(String s:operation.getResponses().keySet()){
                                if(s.startsWith("2")){
                                    x2=true;
                                }else if(s.startsWith("3")){
                                    x3=true;
                                }else if(s.startsWith("4")){
                                    x4=true;
                                }else if(s.startsWith("5")){
                                    x5=true;
                                }

                                if(status.containsKey(s)){
                                    status.put(s,status.get(s)+1);
                                }else{
                                    status.put(s,1);
                                }
                                ApiResponse response=operation.getResponses().get(s);
                                if(response.getLinks()!=null){//检测是否实现hateoas原则
                                    this.hateoasStatic=true;
                                }
                                if(response.getHeaders()!=null){
                                    for(String headerName:response.getHeaders().keySet()){
                                        headerName=headerName.toLowerCase();
                                        if(headerName.equals("cache-control") ){
                                            hasCacheControlStatic=true;
                                        }else if( headerName.equals("expires")){
                                            hasExpiresStatic=true;
                                        }else if( headerName.equals("date") ){
                                            hasDateStatic=true;

                                        }else if(headerName.equals("etag") ){
                                            this.hasEtagStatic=true;
                                        } else if(headerName.equals("last-modified")){
                                            this.hasLastModifiedStatic=true;

                                        }else if(headerName.equals("content-type") ){
                                            this.hasResponseContentTypeStatic=true;
                                        }
                                    }
                                }
                            }
                        }
                        x2s+=x2?1:0;
                        x3s+=x3?1:0;
                        x4s+=x4?1:0;
                        x5s+=x5?1:0;
                    }
                }
                hasStrongCacheStatic=hasCacheControlStatic || hasDateStatic || hasExpiresStatic;
                hasEtagStatic=this.hasEtagStatic || hasLastModifiedStatic;
                /*validateResult.put("hasCacheControl",this.hasCacheControl);
                validateResult.put("hasDate",this.hasDate);
                validateResult.put("hasExpires",this.hasExpires);
                validateResult.put("hasStrongCacheStatic",this.hasStrongCacheStatic);
                validateResult.put("hasEtag",this.hasEtag);
                validateResult.put("hasLastModified",this.hasLastModified);
                validateResult.put("hasEtagStatic",this.hasEtagStatic);
                validateResult.put("hateoas",this.hateoas);*/

                status.put("opcount",opCount);
                statusUsage= new int[]{opCount, x2s, x3s, x4s, x5s};

                if(parameters.size()!=0){//对属性进行检测
                    for(Parameter parameter:parameters){
                        if(parameter.getName()!=null){
                            String paraName=parameter.getName().toLowerCase();
                            if( parameter.getIn().equals("query")){//查询属性
                                if(isPagePara(paraName)){//功能性属性
                                    this.querypara.add(paraName);
                                    setHasPagePara(true);
                                    System.out.println(paraName+" is page parameter. ");
                                }else if(paraName.contains("version")){//版本信息
                                    this.versionInQueryPara=true;
                                    System.out.println("Query-parameter shouldn't has version parameter: "+paraName);
                                }
                            }else if(parameter.getIn().equals("header")){//头文件属性
                                if(paraName.contains("version")){
                                    this.versionInHead=true;
                                }else if(paraName.contains("key") ){
                                    this.hasKey=true;
                                } else if(paraName.contains("token")){
                                    this.hasToken=true;
                                } else  if(paraName.contains("authorization") ){
                                    this.hasAuthorization=true;

                                }else if(paraName.equals("accept")){
                                    this.hasAccept=true;
                                }
                            }
                        }

                    }

                }
                this.securityInHeadPara=this.hasKey || this.hasToken || this.hasAuthorization;
                /*validateResult.put("hasPagePara",isHasPagePara());
                validateResult.put("pageParaList",getQuerypara());
                validateResult.put("versionInQueryPara",this.versionInQueryPara);
                validateResult.put("hasSecurityInHeadPara",this.securityInHeadPara);
                validateResult.put("hasKey",this.hasKey);
                validateResult.put("hasToken",this.hasToken);
                validateResult.put("hasAuthorization",this.hasAuthorization);
                validateResult.put("versionInHeader",this.versionInHead);
                validateResult.put("hasAccpet",this.hasAccept);*/
                this.score=scoreCalculate();
                setValidateResult();

                
            }
        }
        evaluations.put("endpointNum",String.valueOf(this.endpointNum));//将端点数填入评估结果

        // do actual JSON schema validation
        JsonSchema schema = null;
        try {
            schema = getSchema(isVersion2);
            ProcessingReport report = schema.validate(spec);
            ListProcessingReport lp = new ListProcessingReport();
            lp.mergeWith(report);
            java.util.Iterator<ProcessingMessage> it = lp.iterator();
            while (it.hasNext()) {
                ProcessingMessage pm = it.next();
                output.addValidationMessage(new SchemaValidationError(pm.asJson()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }




        return output;
    }

    /**
     * REST API评估得分计算
     */
    private float scoreCalculate() {
        float benchmark=Float.parseFloat(ConfigManager.getInstance().getValue("BENCHMARK"));
        float ca=0,ca1t=0,ca2t=0,ca3t=0,ca4t=0;
        float cb=0,cb1t=0,cb2t=0;
        float cc=0,cc1t=0,cc2t=0;
        float cd=0,cd1t=0,cd2t=0,cd3t=0;
        if(pathEvaData[6]/pathNum>benchmark){
            ca1t++;
        }
        if(pathEvaData[0]/pathNum>benchmark){
            ca1t++;
        }
        if(pathEvaData[1]/pathNum>benchmark){
            ca1t++;
        }
        if(pathEvaData[5]/pathNum>benchmark){
            ca1t++;
        }
        ca+=ca1t/4*Float.parseFloat(ConfigManager.getInstance().getValue("WA1"));
        if(hasContextedRelation){
            ca2t++;
        }
        ca2t++;
        ca+=ca2t/2*Float.parseFloat(ConfigManager.getInstance().getValue("WA2"));
        if(pathEvaData[4]/pathNum>benchmark){
            ca3t++;
        }
        if(pathEvaData[3]/pathNum>benchmark){
            ca3t++;
        }
        if(apiInServer){
            ca3t++;
        }
        ca+=ca3t/3*Float.parseFloat(ConfigManager.getInstance().getValue("WA3"));
        if(hasPagePara){
            ca4t++;
        }
        ca+=ca4t*Float.parseFloat(ConfigManager.getInstance().getValue("WA4"));

        if(!hasWrongPost){
            cb1t++;
        }
        cb1t++;
        cb+=cb1t/2*Float.parseFloat(ConfigManager.getInstance().getValue("WB1"));
        if(!hasWrongStatus){
            cb2t++;
        }
        cb+=cb2t*Float.parseFloat(ConfigManager.getInstance().getValue("WB2"));

        if(hasResponseContentTypeStatic){
            cc1t++;
        }
        if(hasAccept){
            cc1t++;
        }
        if(securityInHeadPara){
            cc1t++;
        }
        cc+=cc1t/3*Float.parseFloat(ConfigManager.getInstance().getValue("WC1"));
        if(hateoasStatic){
            cc2t++;
        }
        cc+=cc2t*Float.parseFloat(ConfigManager.getInstance().getValue("WC2"));
        if(versionInHead){
            cd1t++;
        }
        if(pathEvaData[2]/pathNum>benchmark){
            cd1t++;
        }
        if(!versionInQueryPara){
            cd1t++;
        }
        cd+=cd1t/3*Float.parseFloat(ConfigManager.getInstance().getValue("WD1"));
        if(getSecurity().size()!=0){
            cd2t++;
        }
        cd+=cd2t*Float.parseFloat(ConfigManager.getInstance().getValue("WD2"));
        if(hasStrongCacheStatic){
            cd3t++;
        }
        if(hasNegCacheStatic){
            cd3t++;
        }
        cd+=cd3t/2*Float.parseFloat(ConfigManager.getInstance().getValue("WD3"));
        return ca*Float.parseFloat(ConfigManager.getInstance().getValue("WA"))
                +cb*Float.parseFloat(ConfigManager.getInstance().getValue("WB"))
                +cc*Float.parseFloat(ConfigManager.getInstance().getValue("WC"))
                +cd*Float.parseFloat(ConfigManager.getInstance().getValue("WD"));
    }

    /**
     * 构建路径必须属性散列表
     * @param pathName 路径
     * @param pName 属性名
     * @param pIn 属性位置
     */
    private void buildPathParameterMap(String pathName, String pName, String pIn) {
        String ppname="";
        if(pIn.equals("path")){
            ppname=StanfordNLP.removeBrace(pathName.substring(0,pathName.indexOf("{"+pName+"}")));
        }
        else{
            ppname=StanfordNLP.removeBrace(pathName);
        }
        ppname=StanfordNLP.removeSlash(ppname);//去除多余/和尾/
        if(pathParameterMap.containsKey(ppname)){
            List<String> pvalues=new ArrayList<>();
            pathParameterMap.get(ppname).put(pName,pvalues);

        }else{
            Map<String,List<String>> paras=new HashMap<>();
            List<String> pvalues=new ArrayList<>();
            paras.put(pName,pvalues);
            pathParameterMap.put(ppname,paras);

        }
    }

    /**
     * 检测域名内容（“api”以及版本信息）
     * @param serverurl 服务器信息（OAS3）或域名+baseurl（OAS2）
     */
    private void serverEvaluate(String serverurl) {
        Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
        Matcher m1 = pattern1.matcher(serverurl); // 获取 matcher 对象
        Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
        Matcher m2=pattern2.matcher(serverurl);
        if(serverurl.contains("api")){//检测域名中包含“api”
            this.apiInServer=true;
            System.out.println(serverurl+"has api");
        }else if(m2.find()){
            this.versionInHost=true;
            String version=m2.group();
            if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                this.semanticVersion=true;
            }
        }

    }

    private void pathSemanticsEvaluate(Set paths) {
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            String p = (String) it.next();
            p=p.replace('/',' ');
        }
    }

    /**
     * 从restModel获取基本信息
     * @param model
     */
    private void basicInfoGet(RESTModel model) {
        setPathNum(model.getPaths().size());//提取路径数
//        validateResult.put("pathNum",getPathNum());

        for(String pathName : model.getPaths().keySet()){
            PathRESTer path= (PathRESTer) model.getPaths().get(pathName);
            List<OperationRESTer> operationsInAPath = path.getOperations();
            this.endpointNum+=operationsInAPath.size();//统计端点数
            for(OperationRESTer op:operationsInAPath){
                switch (op.getMethod()){
                    case "get":
                        this.opGet++;
                        break;
                    case "post":
                        this.opPost++;
                        break;
                    case "delete":
                        this.opDelete++;
                        break;
                    case "put":
                        this.opPut++;
                        break;
                    case "head":
                        this.opHead++;
                        break;
                    case "options":
                        this.opOptions++;
                        break;
                    case "patch":
                        this.opPatch++;
                        break;
                    case "trace":
                        this.opTrace++;
                        break;
                    default:break;
                }
            }
        }


    }

    /**
    *@Description: 规范3.0中提取基本信息（路径数，端点数，操作数（get，post，delete，put，，，）
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/7
    */
    private void basicInfoGet(SwaggerParseResult result) {
        setPathNum(result.getOpenAPI().getPaths().keySet().size());//提取路径数
        validateResult.put("pathNum",getPathNum());

        for(String pathName : result.getOpenAPI().getPaths().keySet()){
            OpenAPIDeserializer deserializer = new OpenAPIDeserializer();
            List<Operation> operationsInAPath = deserializer.getAllOperationsInAPath(result.getOpenAPI().getPaths().get(pathName));
            this.endpointNum+=operationsInAPath.size();//统计端点数
            PathItem path = result.getOpenAPI().getPaths().get(pathName);
            if (path.getGet() != null) {
                this.opGet++;
            }
            this.opPost = path.getPost() != null ? this.opPost+1 : this.opPost;
            this.opDelete = path.getDelete() != null ? this.opDelete+1 : this.opDelete;
            this.opPut = path.getPut() != null ? this.opPut+1 : this.opPut;
            this.opHead = path.getHead() != null ? this.opHead+1 : this.opHead;
            this.opPatch = path.getPatch() != null ? this.opPatch+1 : this.opPatch;
            this.opOptions = path.getOptions() != null ? this.opOptions+1 : this.opOptions;
            this.opTrace = path.getTrace() != null ? this.opTrace+1 : this.opTrace;
        }


    }

    /**
    *@Description: 规范2.0中提取基本信息（路径数，端点数，操作数（get，post，delete，put，，，）
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/7
    */
    private void basicInfoGet(SwaggerDeserializationResult result) {
        setPathNum(result.getSwagger().getPaths().keySet().size());//提取路径数
        validateResult.put("pathNum",getPathNum());
        for(String pathName : result.getSwagger().getPaths().keySet()) {
            Path path = result.getSwagger().getPath(pathName);
            List<io.swagger.models.Operation> operations = getAllOperationsInAPath(result.getSwagger().getPath(pathName));
            this.endpointNum += operations.size();//统计端点数
            if (path.getGet() != null) {
                this.opGet++;
            }
            this.opPost = path.getPost() != null ? this.opPost+1 : this.opPost;
            this.opDelete = path.getDelete() != null ? this.opDelete+1 : this.opDelete;
            this.opPut = path.getPut() != null ? this.opPut+1 : this.opPut;
            this.opHead = path.getHead() != null ? this.opHead+1 : this.opHead;
            this.opPatch = path.getPatch() != null ? this.opPatch+1 : this.opPatch;
            this.opOptions = path.getOptions() != null ? this.opOptions+1 : this.opOptions;
        }
        /*validateResult.put("endpointNum",this.getEndpointNum());
        validateResult.put("opGET",this.getOpGet());
        validateResult.put("opPOST",this.getOpPost());
        validateResult.put("opDELETE",getOpDelete());
        validateResult.put("opPUT",getOpPut());
        validateResult.put("opHEAD",getOpHead());
        validateResult.put("opPATCH",getOpPatch());
        validateResult.put("opOPTIONS",getOpOptions());
        validateResult.put("opTRACE",getOpTrace());*/
    }

    /**
    *@Description: 获取API类别信息 2.0规范
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/5
    */
    private void setCategory(SwaggerDeserializationResult result) {
        if(result.getSwagger().getInfo()!=null){
            Map<String, Object> extension = result.getSwagger().getInfo().getVendorExtensions();
            String cateInfo="";
            if(extension!=null && extension.size()!=0){
                if(extension.containsKey("x-apisguru-categories")){
                    cateInfo = extension.get("x-apisguru-categories").toString();
                }
                if(cateInfo.length()!=0){
                    this.category=cateInfo;
                }
            }
        }


        return;
    }

    /**
    *@Description: 获取API类别信息 3.0规范
    *@Param: [result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/7/5
    */
    private void setCategory(SwaggerParseResult result) {
        if(result.getOpenAPI().getInfo()!=null){
            Map<String, Object> extension = result.getOpenAPI().getInfo().getExtensions();
            String cateInfo="";
            if(extension!=null && extension.size()!=0){
                if(extension.containsKey("x-apisguru-categories")){
                    cateInfo = extension.get("x-apisguru-categories").toString();
                }

                if(cateInfo.length()!=0){
                    this.category=cateInfo;
                }
            }
        }

        /*validateResult.put("category",getCategory());*/
        return;
    }

    /**
    *@Description: 头文件检测，检测结果加入evaluations
    *@Param: [headers]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/5/20
    */
    private void headerEvaluate(String url, Header[] headers,Map<String,Object> pathResult) {
        //Map<String,Object> pathResult=new HashMap<>();
        System.out.println("Start header evaluate!");
        System.out.println("header：");
        boolean hasCacheScheme=false;
        boolean hasEtag=false;
        boolean hasDate=false;
        boolean hasCacheControl=false;
        boolean hasContentType=false;
        boolean hasLastModified=false;
        boolean hasExpires=false;
        String contentType="";
        for(Header header:headers){
//            System.out.println(header.getName());
            if(header.getName().toLowerCase().equals("etag")){
//                System.out.println(url+" response has etag");
                hasEtag=true;

            }else if(header.getName().toLowerCase().equals("last-modified")){
//                System.out.println(url+" response has last-modified");
                hasLastModified=true;
            }else if(header.getName().toLowerCase().equals("expires")){
//                System.out.println(url+" response has expires");
                hasExpires=true;
            }else if(header.getName().toLowerCase().equals("cache-control")){
//                System.out.println(url+" response has cache-control");
                hasCacheControl=true;
            }else if(header.getName().toLowerCase().equals("date")){
//                System.out.println(url+" response has date");
                hasDate=true;
            }else if(header.getName().toLowerCase().equals("content-type")){

                //evaluations.put("content-type",header.getValue());
                contentType=header.getValue();
                hasContentType=true;
//                System.out.println(url+" response has content-type:"+contentType);
            }
        }
        hasCacheScheme=hasCacheControl || hasEtag || hasExpires || hasLastModified;
        /*evaluations.put("hasEtag",String.valueOf(hasEtag));
        evaluations.put("hasLastModified",String.valueOf(hasLastModified));
        evaluations.put("hasExpires",String.valueOf(hasExpires));
        evaluations.put("hasCacheControl",String.valueOf(hasCacheControl));
        evaluations.put("hasCacheScheme",String.valueOf(hasCacheScheme));
        evaluations.put("hasContentType",hasContentType==true?contentType:"false");*/
        pathResult.put("hasCacheScheme",hasCacheScheme);
        pathResult.put("hasEtag",hasEtag);
        pathResult.put("hasDate",hasDate);
        pathResult.put("hasExpires",hasExpires);
        pathResult.put("hasLastModified",hasLastModified);
        pathResult.put("hasCacheControl",hasCacheControl);
        pathResult.put("hasContentType",hasContentType);
        pathResult.put("contentType",contentType);
        System.out.println("header evaluate end.");

        this.hasExpires=hasExpires==true?true:this.hasExpires;
        this.hasCacheControl=hasCacheControl==true?true:this.hasCacheControl;
        this.hasEtag=hasEtag==true?true:this.hasEtag;
        this.hasLastModified=hasLastModified==true?true:this.hasLastModified;
        this.hasDate=hasDate==true?true:this.hasDate;
        this.hasResponseContentType=hasContentType==true?true:this.hasResponseContentType;



    }

    /**
    *@Description: 是否有页面过滤机制
    *@Param: [name]
    *@return: boolean
    *@Author: zhouxinyu
    *@date: 2020/5/27
    */
    public boolean isPagePara(String name) {
        if(name==null) return  false;
        //String pageNames[]=PAGEPARANAMES;
        String pageNames[]=ConfigManager.getInstance().getValue("PAGEPARANAMES").split(",",-1);//配置文件获取功能性（页面过滤）查询属性检查列表
        boolean result = false;
        for(int i=0; i< pageNames.length; i++){
            if (name.toLowerCase().indexOf(pageNames[i]) >=0) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * 获取一个路径中的所有操作list
     * @param pathObj
     * @return
     */
    public List<io.swagger.models.Operation> getAllOperationsInAPath(Path pathObj) {
        List<io.swagger.models.Operation> operations = new ArrayList();
        addToOperationsList(operations, pathObj.getGet());
        addToOperationsList(operations, pathObj.getPut());
        addToOperationsList(operations, pathObj.getPost());
        addToOperationsList(operations, pathObj.getPatch());
        addToOperationsList(operations, pathObj.getDelete());
        addToOperationsList(operations, pathObj.getOptions());
        addToOperationsList(operations, pathObj.getHead());
        return operations;
    }

    /**
     * OAS2规范 获取一个路径中的所有操作Map
     * @param pathObj
     * @return
     */
    public Map<String,io.swagger.models.Operation> getAllOperationsMapInAPath(Path pathObj) {
        Map<String,io.swagger.models.Operation> operations = new HashMap<>();
        operations.put("get",pathObj.getGet());
        operations.put("put",pathObj.getPut());
        operations.put("delete",pathObj.getDelete());
        operations.put("post",pathObj.getPost());
        operations.put("patch",pathObj.getPatch());
        operations.put("options",pathObj.getOptions());
        operations.put("head",pathObj.getHead());
        return operations;
    }

    /**
     * OAS3规范获取一个路径下的所有端点
     * @param pathObj
     * @return
     */
    public Map<String, io.swagger.v3.oas.models.Operation> getAllOperationsMapInAPath(PathItem pathObj) {
        Map<String, io.swagger.v3.oas.models.Operation> operations = new HashMap<>();
        operations.put("get",pathObj.getGet());
        operations.put("put",pathObj.getPut());
        operations.put("delete",pathObj.getDelete());
        operations.put("post",pathObj.getPost());
        operations.put("patch",pathObj.getPatch());
        operations.put("options",pathObj.getOptions());
        operations.put("head",pathObj.getHead());
        return operations;
    }

    private void addToOperationsList(List<io.swagger.models.Operation> operationsList, io.swagger.models.Operation operation) {
        if (operation != null) {
            operationsList.add(operation);
        }
    }

    /**
    *@Description: 路径（命名）验证,v2.0
    *@Param: [paths, result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/8/12
    */
    private void pathEvaluate(Set paths, SwaggerDeserializationResult result)  {
        //setPathNum(paths.size());//提取路径数
        System.out.println("jwnl start");
        JWNLwordnet jwnLwordnet= null;
        try {
            jwnLwordnet = new JWNLwordnet();
        } catch (JWNLException e) {
            e.printStackTrace();
        }
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();

            /*String[] subPs=p.split("/");
            String nodetemp=null;
            if(!subPs[0].startsWith("{")){
                nodetemp=subPs[0];
                if(pathTree.containsKey(subP)){

                }else{

                }

            }*/

            //evaluateToScore()
            if(p.contains("_")){
                //System.out.println(p+" has _");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("no_",false);
            }else {
                this.pathEvaData[0]++;//Integer是Object子类，是对象，可以为null。int是基本数据类型，必须初始化，默认为0
                pathResult.put("no_",true);
            }

            if(p!=p.toLowerCase()){
                //System.out.println(p+"need to be lowercase");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("lowercase",false);
            }else {
                this.pathEvaData[1]++;
                pathResult.put("lowercase",true);
            }

            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
            Matcher m2=pattern2.matcher(p);
            if(m2.find()){
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                String version=m2.group();
                this.versionInPath=true;
                if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                    this.semanticVersion=true;
                }
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }
            if(p.toLowerCase().contains("api")){
                System.out.println("api shouldn't in path "+p);
                //this.score=this.score-10>0?this.score-10:0;
                pathResult.put("noapi",false);
            }else {
                this.pathEvaData[3]++;
                pathResult.put("noapi",true);
            }

            //this.pathlist.add(p);
            Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
            Matcher m = pp.matcher(p);
            String pathclear = "";//去除属性{}之后的路径
            int endtemp=0;
            while(m.find()){
                pathclear+=p.substring(endtemp,m.start());
                endtemp=m.end();
            }
            pathclear+=p.substring(endtemp);
            pathclear=pathclear.toLowerCase();
            //String crudnames[]=CRUDNAMES;
            String crudnames[]=ConfigManager.getInstance().getValue("CRUDNAMES").split(",",-1);

            String dellistString=ConfigManager.getInstance().getValue("DELLIST");
            String str1[] = dellistString.split(";",-1);
            String delList[][]=new String[str1.length][];
            for(int i = 0;i < str1.length;i++) {

                String str2[] = str1[i].split(",");
                delList[i] = str2;
            }
            //String delList[][]=DELLIST;
            boolean isCrudy = false;
            List<String> verblist=new ArrayList<>();
            for(int i=0; i< crudnames.length; i++){
                // notice it should start with the CRUD name
                String temp=fileHandle.delListFromString(pathclear,delList[i]);
                if (temp.contains(crudnames[i])) {
                    isCrudy = true;
                    verblist.add(crudnames[i]);
                    if(crudnames[i]!="create" && crudnames[i]!="add" && crudnames[i]!="post" && crudnames[i]!="new" && crudnames[i]!="push" ){
                        this.hasWrongPost=true;
                    }
                    CRUDPathOperation(p,crudnames[i], result);
                    break;
                }
            }
            this.CRUDlist.addAll(verblist);
            pathResult.put("CRUDlist",verblist);
            if(isCrudy){
                System.out.println("CRUD shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noCRUD",false);

            }else{
                this.pathEvaData[4]++;
                pathResult.put("noCRUD",true);
            }
            //层级之间的语义上下文关系
            List<String> splitPaths;
            String pathText=pathclear.replace("/"," ");
            splitPaths=StanfordNLP.getlemma(pathText);//词形还原
            if(splitPaths.size()>=2){
                /*WordNet wordNet=new WordNet();
                wordNet.hasRelation(splitPaths);//检测是否具有上下文关系*/
                try {
                    if (jwnLwordnet!=null && jwnLwordnet.hasRelation(splitPaths)){
                        this.hasContextedRelation=true;
                    }
                } catch (JWNLException e) {
                    e.printStackTrace();
                }

            }

            //文件扩展名不应该包含在API的URL命名中
            //String suffix[]=SUFFIX_NAMES;
            String suffix[]=ConfigManager.getInstance().getValue("SUFFIX_NAMES").split(",",-1);
            boolean isSuffix = false;
            List<String> slist=new ArrayList<>();
            for(int i=0; i< suffix.length; i++){
                if (p.toLowerCase().indexOf(suffix[i]) >=0) {
                    isSuffix = true;
                    slist.add(suffix[i]);

                    break;
                }
            }
            this.suffixlist.addAll(slist);
            pathResult.put("suffixList",slist);
            if(isSuffix){
                System.out.println("suffix shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noSuffix",false);
            }else {
                this.pathEvaData[5]++;
                pathResult.put("noSuffix",true);
            }



            //使用正斜杠分隔符“/”来表示一个层次关系，尾斜杠不包含在URL中
            int hierarchyNum=0;
            if(p.endsWith("/") && p.length()>1){
                //System.out.println(p+" :尾斜杠不包含在URL中");
                //this.score=this.score-20>0?this.score-20:0;
                hierarchyNum=substringCount(p,"/")-1;
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数
                pathResult.put("noend/",false);

            }else{
                pathResult.put("noend/",true);
                this.pathEvaData[6]++;
                //建议嵌套深度一般不超过3层
                hierarchyNum=substringCount(p,"/");
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数

            }
            pathResult.put("hierarchies",hierarchyNum);
            if(hierarchyNum>3){
                //System.out.println(p+": 嵌套深度建议不超过3层");
                //this.score=this.score-5>0?this.score-5:0;
            }else {

            }
            pathDetail.put(p,pathResult);

        }
        validateResult.put("pathEvaData",getPathEvaData());
        setAvgHierarchy(this.pathEvaData[7]/(float)paths.size());//计算平均层级数
        /*validateResult.put("avgHierarchies",getAvgHierarchy());
        validateResult.put("versionInPath",this.versionInPath);
        validateResult.put("semanticVersion",this.semanticVersion);*/
        /*evaluations.put("avgHierarchy",Float.toString(getAvgHierarchy()));//向评估结果中填入平均层级数
        evaluations.put("maxHierarchy",Float.toString(pathEvaData[8]));//最大层级数
        evaluations.put("noUnderscoreRate",Float.toString(pathEvaData[0]/getPathNum()));//不出现下划线实现率
        evaluations.put("lowcaseRate",Float.toString(pathEvaData[1]/getPathNum()));//小写实现率
        evaluations.put("noVersionRate",Float.toString(pathEvaData[2]/getPathNum()));//不出现版本信息实现率
        evaluations.put("noapiRate",Float.toString(pathEvaData[3]/getPathNum()));//不出现"api"实现率
        evaluations.put("noCRUDRate",Float.toString(pathEvaData[4]/getPathNum()));//不出现动词实现率
        evaluations.put("noSuffixRate",Float.toString(pathEvaData[5]/getPathNum()));//不出现格式后缀实现率
        evaluations.put("noEndSlashRate",Float.toString(pathEvaData[6]/getPathNum()));//没有尾斜杠实现率*/

        /*validateResult.put("path",pathDetail);*/
        System.out.println("end path evaluate");
    }

    /**
     * RESTer模型的路径检测（通用）
     * @param paths
     * @param result
     * @throws IOException
     * @throws JWNLException
     */
    private void pathEvaluate(Set paths, RESTModel result)  {
        //setPathNum(paths.size());//提取路径数
        System.out.println("jwnl start");
        JWNLwordnet jwnLwordnet= null;
        try {
            jwnLwordnet = new JWNLwordnet();
        } catch (JWNLException e) {
            e.printStackTrace();
        }
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();
            //evaluateToScore()
            if(p.contains("_")){
                //System.out.println(p+" has _");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("no_",false);
            }else {
                this.pathEvaData[0]++;//Integer是Object子类，是对象，可以为null。int是基本数据类型，必须初始化，默认为0
                pathResult.put("no_",true);
            }

            if(!p.equals(p.toLowerCase())){
                //System.out.println(p+"need to be lowercase");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("lowercase",false);
            }else {
                this.pathEvaData[1]++;
                pathResult.put("lowercase",true);
            }

            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
            Matcher m2=pattern2.matcher(p);
            if(m2.find()){
                this.versionInPath=true;
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                String version=m2.group();
                int dotCount=0;
                for(int i=0;i<version.length();i++){
                    if(version.charAt(i)=='.'){
                        dotCount++;
                    }
                }
                this.dotCountInPath=dotCount;
                if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                    this.semanticVersion=true;
                }
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }
            if(p.toLowerCase().indexOf("api")>=0){
                System.out.println("api shouldn't in path "+p);
                //this.score=this.score-10>0?this.score-10:0;
                pathResult.put("noapi",false);
            }else {
                this.pathEvaData[3]++;
                pathResult.put("noapi",true);
            }

            //this.pathlist.add(p);
            Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
            Matcher m = pp.matcher(p);
            String pathclear = "";//去除属性{}之后的路径
            int endtemp=0;
            while(m.find()){
                pathclear+=p.substring(endtemp,m.start());
                endtemp=m.end();
            }
            pathclear+=p.substring(endtemp);
            pathclear=pathclear.toLowerCase();

            //String crudnames[]=CRUDNAMES;
            String crudnames[]=ConfigManager.getInstance().getValue("CRUDNAMES").split(",",-1);

            String dellistString=ConfigManager.getInstance().getValue("DELLIST");
            String str1[] = dellistString.split(";",-1);
            String delList[][]=new String[str1.length][];
            for(int i = 0;i < str1.length;i++) {

                String str2[] = str1[i].split(",");
                delList[i] = str2;
            }
            //String delList[][]=DELLIST;
            boolean isCrudy = false;
            List<String> verblist=new ArrayList<>();
            for(int i=0; i< crudnames.length; i++){
                // notice it should start with the CRUD name
                String temp=fileHandle.delListFromString(pathclear,delList[i]);
                if (temp.contains(crudnames[i])) {
                    isCrudy = true;
                    verblist.add(crudnames[i]);
                    if(crudnames[i]!="create" && crudnames[i]!="add" && crudnames[i]!="post" && crudnames[i]!="new" && crudnames[i]!="push" ){
                        this.hasWrongPost=true;
                    }
                    CRUDPathOperation(p,crudnames[i], result);
                    break;
                }
            }
            this.CRUDlist.addAll(verblist);
            pathResult.put("CRUDlist",verblist);
            if(isCrudy){
                System.out.println("CRUD shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noCRUD",false);

            }else{
                this.pathEvaData[4]++;
                pathResult.put("noCRUD",true);
            }
            //层级之间的语义上下文关系
            List<String> splitPaths;
            String pathText=pathclear.replace("/"," ");
            splitPaths=StanfordNLP.getlemma(pathText);//词形还原
            if(splitPaths.size()>=2){
                /*WordNet wordNet=new WordNet();
                wordNet.hasRelation(splitPaths);//检测是否具有上下文关系*/
                try {
                    if (jwnLwordnet!=null && jwnLwordnet.hasRelation(splitPaths)){
                        this.hasContextedRelation=true;
                    }
                } catch (JWNLException e) {
                    e.printStackTrace();
                }
            }

            //文件扩展名不应该包含在API的URL命名中
            //String suffix[]=SUFFIX_NAMES;
            String suffix[]=ConfigManager.getInstance().getValue("SUFFIX_NAMES").split(",",-1);
            boolean isSuffix = false;
            List<String> slist=new ArrayList<>();
            for(int i=0; i< suffix.length; i++){
                if (p.toLowerCase().indexOf(suffix[i]) >=0) {
                    isSuffix = true;
                    slist.add(suffix[i]);

                    break;
                }
            }
            this.suffixlist.addAll(slist);
            pathResult.put("suffixList",slist);
            if(isSuffix){
                System.out.println("suffix shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noSuffix",false);
            }else {
                this.pathEvaData[5]++;
                pathResult.put("noSuffix",true);
            }



            //使用正斜杠分隔符“/”来表示一个层次关系，尾斜杠不包含在URL中
            int hierarchyNum=0;
            if(p.endsWith("/") && p.length()>1){
                //System.out.println(p+" :尾斜杠不包含在URL中");
                //this.score=this.score-20>0?this.score-20:0;
                hierarchyNum=substringCount(p,"/")-1;
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数
                pathResult.put("noend/",false);

            }else{
                pathResult.put("noend/",true);
                this.pathEvaData[6]++;
                //建议嵌套深度一般不超过3层
                hierarchyNum=substringCount(p,"/");
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数

            }
            pathResult.put("hierarchies",hierarchyNum);
            if(hierarchyNum>3){
                //System.out.println(p+": 嵌套深度建议不超过3层");
                //this.score=this.score-5>0?this.score-5:0;
            }else {

            }
            pathDetail.put(p,pathResult);
        }
        setAvgHierarchy(this.pathEvaData[7]/(float)paths.size());//计算平均层级数
        validateResult.put("pathEvaData",getPathEvaData());
        /*validateResult.put("versionInPath",this.versionInPath);
        validateResult.put("semanticVersion",this.semanticVersion);

        validateResult.put("avgHierarchies",getAvgHierarchy());


        validateResult.put("path",pathDetail);*/
    }
    /**
    *@Description: 路径（命名）验证,v3.0
    *@Param: [paths]路径名集合
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/5/16
    */
    private void pathEvaluate(Set paths, SwaggerParseResult result)  {
        //setPathNum(paths.size());//提取路径数
        System.out.println("jwnl start");
        JWNLwordnet jwnLwordnet= null;
        try {
            jwnLwordnet = new JWNLwordnet();
        } catch (JWNLException e) {
            e.printStackTrace();
        }
        evaluations.put("pathNum",Float.toString(getPathNum()));//向评估结果中填入路径数
        for (Iterator it = paths.iterator(); it.hasNext(); ) {
            Map<String,Object> pathResult=new HashMap<>();
            String p = (String) it.next();
            //evaluateToScore()
            if(!(p.indexOf("_") < 0)){
                //System.out.println(p+" has _");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("no_",false);
            }else {
                this.pathEvaData[0]++;//Integer是Object子类，是对象，可以为null。int是基本数据类型，必须初始化，默认为0
                pathResult.put("no_",true);
            }

            if(p!=p.toLowerCase()){
                //System.out.println(p+"need to be lowercase");
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("lowercase",false);
            }else {
                this.pathEvaData[1]++;
                pathResult.put("lowercase",true);
            }

            Pattern pattern1 = Pattern.compile(ConfigManager.getInstance().getValue("VERSIONPATH_REGEX"));
            Matcher m1 = pattern1.matcher(p); // 获取 matcher 对象
            Pattern pattern2=Pattern.compile("v(ers?|ersion)?[0-9.]+(-?(alpha|beta|rc)([0-9.]+\\+?[0-9]?|[0-9]?))?");
            Matcher m2=pattern2.matcher(p);
            if(m2.find()){
                this.versionInPath=true;
                System.out.println("version shouldn't in paths "+p);
                //this.score=this.score-5>0?this.score-5:0;
                String version=m2.group();
                int dotCount=0;
                for(int i=0;i<version.length();i++){
                    if(version.charAt(i)=='.'){
                        dotCount++;
                    }
                }
                this.dotCountInPath=dotCount;
                if(version.contains(".") || version.contains("alpha") || version.contains("beta") || version.contains("rc")){
                    this.semanticVersion=true;
                }
                pathResult.put("noVersion",false);
            }else {
                this.pathEvaData[2]++;
                pathResult.put("noVersion",true);
            }
            if(p.toLowerCase().indexOf("api")>=0){
                System.out.println("api shouldn't in path "+p);
                //this.score=this.score-10>0?this.score-10:0;
                pathResult.put("noapi",false);
            }else {
                this.pathEvaData[3]++;
                pathResult.put("noapi",true);
            }

            //this.pathlist.add(p);
            Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
            Matcher m = pp.matcher(p);
            String pathclear = "";//去除属性{}之后的路径
            int endtemp=0;
            while(m.find()){
                pathclear+=p.substring(endtemp,m.start());
                endtemp=m.end();
            }
            pathclear+=p.substring(endtemp);
            pathclear=pathclear.toLowerCase();

           //String crudnames[]=CRUDNAMES;
            String crudnames[]=ConfigManager.getInstance().getValue("CRUDNAMES").split(",",-1);

            String dellistString=ConfigManager.getInstance().getValue("DELLIST");
            String str1[] = dellistString.split(";",-1);
            String delList[][]=new String[str1.length][];
            for(int i = 0;i < str1.length;i++) {

                String str2[] = str1[i].split(",");
                delList[i] = str2;
            }
            //String delList[][]=DELLIST;
            boolean isCrudy = false;
            List<String> verblist=new ArrayList<>();
            for(int i=0; i< crudnames.length; i++){
                // notice it should start with the CRUD name
                String temp=fileHandle.delListFromString(pathclear,delList[i]);
                if (temp.indexOf(crudnames[i]) >=0) {
                    isCrudy = true;
                    verblist.add(crudnames[i]);
                    if(crudnames[i]!="create" && crudnames[i]!="add" && crudnames[i]!="post" && crudnames[i]!="new" && crudnames[i]!="push" ){
                        this.hasWrongPost=true;
                    }
                    CRUDPathOperation(p,crudnames[i], result);
                    break;
                }
            }
            this.CRUDlist.addAll(verblist);
            pathResult.put("CRUDlist",verblist);
            if(isCrudy){
                System.out.println("CRUD shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noCRUD",false);

            }else{
                this.pathEvaData[4]++;
                pathResult.put("noCRUD",true);
            }
            //层级之间的语义上下文关系
            List<String> splitPaths;
            String pathText=pathclear.replace("/"," ");
            splitPaths=StanfordNLP.getlemma(pathText);//词形还原
            if(splitPaths.size()>=2){
                /*WordNet wordNet=new WordNet();
                wordNet.hasRelation(splitPaths);//检测是否具有上下文关系*/
                try {
                    if (jwnLwordnet!=null && jwnLwordnet.hasRelation(splitPaths)){
                        this.hasContextedRelation=true;
                    }
                } catch (JWNLException e) {
                    e.printStackTrace();
                }
            }

            //文件扩展名不应该包含在API的URL命名中
            //String suffix[]=SUFFIX_NAMES;
            String suffix[]=ConfigManager.getInstance().getValue("SUFFIX_NAMES").split(",",-1);
            boolean isSuffix = false;
            List<String> slist=new ArrayList<>();
            for(int i=0; i< suffix.length; i++){
                if (p.toLowerCase().indexOf(suffix[i]) >=0) {
                    isSuffix = true;
                    slist.add(suffix[i]);

                    break;
                }
            }
            this.suffixlist.addAll(slist);
            pathResult.put("suffixList",slist);
            if(isSuffix){
                System.out.println("suffix shouldn't in path "+p);
                //this.score=this.score-20>0?this.score-20:0;
                pathResult.put("noSuffix",false);
            }else {
                this.pathEvaData[5]++;
                pathResult.put("noSuffix",true);
            }



            //使用正斜杠分隔符“/”来表示一个层次关系，尾斜杠不包含在URL中
            int hierarchyNum=0;
            if(p.endsWith("/") && p.length()>1){
                //System.out.println(p+" :尾斜杠不包含在URL中");
                //this.score=this.score-20>0?this.score-20:0;
                hierarchyNum=substringCount(p,"/")-1;
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数
                pathResult.put("noend/",false);

            }else{
                pathResult.put("noend/",true);
                this.pathEvaData[6]++;
                //建议嵌套深度一般不超过3层
                hierarchyNum=substringCount(p,"/");
                this.hierarchies.add(Integer.toString(hierarchyNum));
                this.pathEvaData[7]+=hierarchyNum;//层级总数，算平均层级数
                this.pathEvaData[8]=hierarchyNum>=this.pathEvaData[8]?hierarchyNum:this.pathEvaData[8];//最大层级数

            }
            pathResult.put("hierarchies",hierarchyNum);
            if(hierarchyNum>3){
                //System.out.println(p+": 嵌套深度建议不超过3层");
                //this.score=this.score-5>0?this.score-5:0;
            }else {

            }
            pathDetail.put(p,pathResult);
        }
        setAvgHierarchy(this.pathEvaData[7]/(float)paths.size());//计算平均层级数
        validateResult.put("pathEvaData",getPathEvaData());
        System.out.println("end path evaluate");
        /*validateResult.put("versionInPath",this.versionInPath);
        validateResult.put("semanticVersion",this.semanticVersion);

        validateResult.put("avgHierarchies",getAvgHierarchy());


        validateResult.put("path",pathDetail);*/
    }

    /**
     * 路径中出现的动词以及所使用的操作-通用
     * @param p
     * @param crudname
     * @param result
     */
    private void CRUDPathOperation(String p, String crudname, RESTModel result) {
        List<String> pathOP=new ArrayList<>();
        pathOP.add(p);
        pathOP.add(crudname);
        PathRESTer path = (PathRESTer) result.getPaths().get(p);
        for(OperationRESTer op : path.getOperations()){
            pathOP.add(op.getMethod());
        }
        CRUDPathOperations.add(pathOP);
    }

    /**
    *@Description: 路径中出现的动词以及所使用的操作 v3.0
    *@Param: [p, crudname, result]
    *@return: void
    *@Author: zhouxinyu
    *@date: 2020/8/12
    */
    private void CRUDPathOperation(String p, String crudname, SwaggerParseResult result) {
        List<String> pathOP=new ArrayList<>();
        pathOP.add(p);
        pathOP.add(crudname);
        PathItem path =result.getOpenAPI().getPaths().get(p);
        for(PathItem.HttpMethod op : path.readOperationsMap().keySet()){
            pathOP.add(op.name());
        }
        CRUDPathOperations.add(pathOP);
    }

    /**
    *@Description: 路径中出现的动词以及所使用的操作 v2.0
    *@Param:
    *@return:
    *@Author: zhouxinyu
    *@date: 2020/8/12
    */
    private void CRUDPathOperation(String p, String crudname, SwaggerDeserializationResult result) {
        List<String> pathOP=new ArrayList<>();
        pathOP.add(p);
        pathOP.add(crudname);
        Path path=result.getSwagger().getPaths().get(p);

        for(HttpMethod op : path.getOperationMap().keySet()){
            pathOP.add(op.name());
        }
        CRUDPathOperations.add(pathOP);
    }

    //正则表达式提取字符串{}内字符串
    public static List<String> extractMessageByRegular(String msg){

        List<String> list=new ArrayList<String>();
        Pattern p = Pattern.compile("(\\{[^\\}]*\\})");
        Matcher m = p.matcher(msg);
        while(m.find()){
            list.add(m.group().substring(1, m.group().length()-1));
        }
        return list;
    }

    /**
    *@Description: 计算字符串中子串数
    *@Param: [s, subs]
    *@return: int
    *@Author: zhouxinyu
    *@date: 2020/5/16
    */
    public int substringCount(String s, String subs) {
        //String src = "Little monkey like to eat bananas, eat more into the big monkey, and finally become fat monkey";
        //String dest = "monkey";
        int count = 0;
        int index = 0;

        while ((index = s.indexOf(subs)) != -1){
            s = s.substring(index + subs.length());
            //System.out.println(src);
            count++;
        }
        //System.out.print(count);
        return count;
    }

    /**
    *@Description: 动态检测指定url的响应内容
    *@Param: [urlString, rejectLocal, rejectRedirect]
    *@return: org.apache.http.Header[]
    *@Author: zhouxinyu
    *@date: 2020/5/18
    */
    public void dynamicValidateByURL(String pathName,Request request, boolean rejectLocal, boolean rejectRedirect) throws IOException, JSONException {
        Map<String,Object> pathResult=new HashMap<>();
        String urlString=request.getUrl();
        System.out.println(urlString);
        pathResult.put("url",urlString);
        String method=request.getMethod();
        if(urlString.contains("{")){
            return  ;
        }else {
            URL url = new URL(urlString);


            if (rejectLocal) {
                InetAddress inetAddress = InetAddress.getByName(url.getHost());
                if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                    throw new IOException("Only accepts http/https protocol");
                }
            }


            RequestConfig.Builder requestBuilder = RequestConfig.custom();//设置配置信息
            requestBuilder = requestBuilder
                    .setConnectTimeout(5000)//连接超时时间
                    .setSocketTimeout(5000);//socket超时时间


            Map<String, String> header=request.getHeader();
            //指定身份认证
            if(urlString.contains("app")){
                header.put("authorization","Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiIxMjM3ODQiLCJleHAiOjE2MjY5NTg4NzMsImlhdCI6MTUxNjIzOTAyMn0.bNqGvRfoScbsjZvkSNx_hip5C5yQNNlyNkDEU_BoVDOTUqgHAUtEYeLeUyubU1BjgPb33YOdCgDLDBC8NWL1XR9tAF7WJ57qoomXq1KhXoIUYIg6EtF6fupLRQ48LvJHAJiC-f_iKDBAlH2Jqr70jvLqiPENhTzB3drHTtqfBsSeR5rQGjN0UOqivUhRb80TQlvPVePQ0MTTZOcydsWIjsogXzzXZr4p9CnGf75nG8aB9Vg9EeAUtdQjn0EnGFJPyx4Uc7UR8W5MRQzI79Dg7fBRzqRnQPkb_yEd0XhWPAsRF7coxqePRcjQz5SAT9e4iZDayvWoCpDXUCwgKdTQLg");

            }else {
                header.put("authorization","token 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");

            }
            header.put("Accept", "application/json, */*");

            /*JSONObject jsonObject=JSONObject.fromObject(request.getEntity());
            String string = jsonObject.toString();//消息体字符串*/
            String string = request.getEntity();//消息体字符串

            System.out.println(method);
            pathResult.put("method",method);
            pathResult.put("header",header);



            if(method=="get"){
                HttpGet httpRequest = new HttpGet(urlString);//创建get请求,此时父类A的变量和静态方法会将子类的变量和静态方法隐藏。instanceA此时唯一可能调用的子类B的地方就是子类B中覆盖了父类A中的实例方法。
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="post"){
                HttpPost httpRequest=new HttpPost(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }

                //设置消息体
                StringEntity entity = new StringEntity(string, "UTF-8");
                httpRequest.setEntity(entity);
                System.out.println("entity: "+string);
                pathResult.put("requestEntity",string);
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);//获得响应
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }

            }else if(method=="put"){
                HttpPut httpRequest=new HttpPut(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }

                //设置消息体
                StringEntity entity = new StringEntity(string, "UTF-8");
                httpRequest.setEntity(entity);
                System.out.println("entity: "+string);
                pathResult.put("requestEntity",string);
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="delete"){
                HttpDelete httpRequest=new HttpDelete(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="head"){
                HttpHead httpRequest=new HttpHead(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="patch"){
                HttpPatch httpRequest=new HttpPatch(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }

                //设置消息体
                StringEntity entity = new StringEntity(string, "UTF-8");
                httpRequest.setEntity(entity);
                System.out.println("entity: "+string);
                pathResult.put("requestEntity",string);
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }else if(method=="options"){
                HttpOptions httpRequest=new HttpOptions(urlString);
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }
            else{
                HttpGet httpRequest = new HttpGet(urlString);//创建get请求
                httpRequest.setConfig(requestBuilder.build());//将上面的配置信息运用到GET请求中

                //设置头文件
                for(String name:header.keySet()){
                    httpRequest.setHeader(name,header.get(name));
                }
                logger.info(urlString+",,"+method+",,"+string);
                final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);//创建HTTP客户端
                if (httpClient != null) {
                    final CloseableHttpResponse response = httpClient.execute(httpRequest);
                    dynamicValidateByResponse(response,pathName,urlString,pathResult);
                    httpClient.close();
                } else {
                    throw new IOException("CloseableHttpClient could not be initialized");
                }
            }


        }
        this.pathDetailDynamic.put(method+" "+pathName,pathResult);//各url的动态检测结果
        return;
    }

    private void dynamicValidateByResponse(CloseableHttpResponse response,String pathName,String urlString,Map<String,Object> pathResult) throws IOException {
        try {

            this.responseNum++;
            StatusLine line = response.getStatusLine();
            System.out.println("response status: "+line.getStatusCode());

            Header[] headers = response.getAllHeaders();//获取头文件
            HttpEntity entity = response.getEntity();//获取响应体
            pathResult.put("status",line.getStatusCode());
            if (line.getStatusCode() > 299 || line.getStatusCode() < 200) {//成功状态
                logger.info("status:"+line.getStatusCode()+",,"+EntityUtils.toString(entity));
                return ;
                //throw new IOException("failed to read swagger with code " + line.getStatusCode());
            }
            this.validResponseNum++;
            logger.info("status:"+line.getStatusCode());
            if(headers!=null){
                headerEvaluate(urlString,headers,pathResult);//对头文件进行检测
                //System.out.println("changesuccess?"+pathResult.size());
            }


            if(entity!=null){
                entityEvaluate(pathName,urlString,entity,pathResult);//检测响应体
            }

            //return EntityUtils.toString(entity, "UTF-8");

        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            response.close();

        }
    }

    /**
     * 响应消息体分析评估
     * @param pathName
     * @param urlString
     * @param entity
     * @param pathResult
     * @throws IOException
     * @throws JSONException
     */
    private void entityEvaluate(String pathName,String urlString, HttpEntity entity,Map<String,Object> pathResult) throws IOException, JSONException {
        System.out.println("Start entity evaluate!");
        String entityString=EntityUtils.toString(entity);
        pathResult.put("responseEntity", entityString);
        //System.out.println("entityString:"+entityString);//打印响应消息体
        Boolean isHATEOAS=false;


        String ppname=StanfordNLP.removeBrace(pathName);
        ppname=StanfordNLP.removeSlash(ppname);

        Map<String,List<String>> pathParameters=pathParameterMap.get(ppname);


       if(entity.getContentType().getValue().contains("application/json")) {//判断响应体格式是否为json
           //JSONObject object = JSONObject.parseObject(entityString);

           //String[] keySet = null;
           //解析为jsonObject或jsonArray
           JSONObject entityObject=null;
            ArrayList<JSONObject> jsonArray=new ArrayList<JSONObject>();
           if(entityString.startsWith("{")) {
               try {
                   entityObject = JSONObject.fromObject(entityString);
                   //keySet=JSONObject.getNames(entityObject);
               }catch (JSONException e){
                   System.out.println("error:"+e.toString());
                   return;
               }

           }else if(entityString.startsWith("[")){
               try {
                   JSONArray entityArray = JSONArray.fromObject(entityString);
                   for (int i = 0; i < entityArray.size(); i++) {
                       JSONObject object = entityArray.getJSONObject(i);
                       jsonArray.add(object);
                       //keySet = JSONObject.getNames(object);
                   }
               }catch (JSONException e){
                   System.out.println("error:"+e.toString());
                   return;
               }
           }
           //检测是否实现HATEOAS原则，即响应体中是否含有link
           if(entityObject!=null){
               jsonArray.add(entityObject);
           }
           for(JSONObject entityjson:jsonArray){
               for(Object key:entityjson.keySet()){
                   if(key.toString().toLowerCase().contains("link")){
                       isHATEOAS=true;
//                       System.out.println(urlString+"has HATEOAS "+key+":"+entityjson.getString(key.toString()));
                   }
                   String entityValue=entityjson.get(key).toString().replace(' ','-');
                   //只检测响应消息的第一层，如果值为对象，跳过不检测
                   if(!entityValue.contains("{") && !entityValue.contains("[")){
                       if (pathParameters != null) {
                           for (String paraName : pathParameters.keySet()) {
                               //精确匹配优先
                               if(key.toString().toLowerCase().contains(paraName.toLowerCase()) || paraName.toLowerCase().contains(key.toString().toLowerCase())){//模糊匹配
                                   List<String> values = pathParameters.get(paraName);
                                   values.add(entityValue);
                                   pathParameters.put(paraName,values);
                               }


                           }
                       }
                   }

               }
           }


           //System.out.println("entityKeyset"+keySet.toString());
       }
       pathResult.put("isHATEOAS-dy",isHATEOAS);
       this.hateoas=isHATEOAS;
        System.out.println("entity evaluate end.");
    }

    private JsonSchema getSchema(boolean isVersion2) throws Exception {
        if (isVersion2) {
            return getSchemaV2();
        } else {
            return getSchemaV3();
        }
    }

    private JsonSchema getSchemaV3() throws Exception {
        if (schemaV3 != null && (System.currentTimeMillis() - LAST_FETCH_V3) < 600000) {
            return schemaV3;
        }

        try {
            LOGGER.debug("returning online schema v3");
            LAST_FETCH_V3 = System.currentTimeMillis();
            schemaV3 = resolveJsonSchema(getUrlContents(SCHEMA_URL), true);
            return schemaV3;
        } catch (Exception e) {
            LOGGER.warn("error fetching schema v3 from GitHub, using local copy");
            schemaV3 = resolveJsonSchema(getResourceFileAsString(SCHEMA_FILE), true);
            LAST_FETCH_V3 = System.currentTimeMillis();
            return schemaV3;
        }
    }

    private JsonSchema getSchemaV2() throws Exception {
        if (schemaV2 != null && (System.currentTimeMillis() - LAST_FETCH) < 600000) {
            return schemaV2;
        }

        try {
            LOGGER.debug("returning online schema");
            LAST_FETCH = System.currentTimeMillis();
            schemaV2 = resolveJsonSchema(getUrlContents(SCHEMA2_URL));
            return schemaV2;
        } catch (Exception e) {
            LOGGER.warn("error fetching schema from GitHub, using local copy");
            schemaV2 = resolveJsonSchema(getResourceFileAsString(SCHEMA2_FILE));
            LAST_FETCH = System.currentTimeMillis();
            return schemaV2;
        }
    }

    private JsonSchema resolveJsonSchema(String schemaAsString) throws Exception {
        return resolveJsonSchema(schemaAsString, false);
    }
    private JsonSchema resolveJsonSchema(String schemaAsString, boolean removeId) throws Exception {
        JsonNode schemaObject = JsonMapper.readTree(schemaAsString);
        if (removeId) {
            ObjectNode oNode = (ObjectNode) schemaObject;
            if (oNode.get("id") != null) {
                oNode.remove("id");
            }
            if (oNode.get("$schema") != null) {
                oNode.remove("$schema");
            }
            if (oNode.get("description") != null) {
                oNode.remove("description");
            }
        }
        JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
        return factory.getJsonSchema(schemaObject);

    }
    private static CloseableHttpClient getCarelessHttpClient(boolean disableRedirect) {
        CloseableHttpClient httpClient = null;

        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            });
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
            HttpClientBuilder httpClientBuilder = HttpClients
                    .custom()
                    .setSSLSocketFactory(sslsf);
            if (disableRedirect) {
                httpClientBuilder.disableRedirectHandling();
            }
            httpClientBuilder.setUserAgent("swagger-validator");
            httpClient = httpClientBuilder.build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            LOGGER.error("can't disable SSL verification", e);
        }

        return httpClient;
    }

    public static String getUrlContents(String urlString) throws IOException {
        return getUrlContents(urlString, false, false);
    }
    public static String getUrlContents(String urlString, boolean rejectLocal, boolean rejectRedirect) throws IOException {
        LOGGER.trace("fetching URL contents");
        URL url = new URL(urlString);
        if(rejectLocal) {
            InetAddress inetAddress = InetAddress.getByName(url.getHost());
            if(inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress()) {
                throw new IOException("Only accepts http/https protocol");
            }
        }
        final CloseableHttpClient httpClient = getCarelessHttpClient(rejectRedirect);

        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        requestBuilder = requestBuilder
                .setConnectTimeout(5000)
                .setSocketTimeout(5000);

        HttpGet getMethod = new HttpGet(urlString);
        getMethod.setConfig(requestBuilder.build());
        getMethod.setHeader("Accept", "application/json, */*");


        if (httpClient != null) {
            final CloseableHttpResponse response = httpClient.execute(getMethod);

            try {

                HttpEntity entity = response.getEntity();
                StatusLine line = response.getStatusLine();
                if(line.getStatusCode() > 299 || line.getStatusCode() < 200) {
                    throw new IOException("failed to read swagger with code " + line.getStatusCode());
                }
                return EntityUtils.toString(entity, "UTF-8");
            } finally {
                response.close();
                httpClient.close();
            }
        } else {
            throw new IOException("CloseableHttpClient could not be initialized");
        }
    }

    private SwaggerParseResult readOpenApi(String content) throws IllegalArgumentException {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        return parser.readContents(content, null, null);

    }

    private SwaggerDeserializationResult readSwagger(String content) throws IllegalArgumentException {
        SwaggerParser parser = new SwaggerParser();
        return parser.readWithInfo(content);
    }

    private JsonNode readNode(String text) {
        try {
            if (text.trim().startsWith("{")) {
                return JsonMapper.readTree(text);
            } else {
                return YamlMapper.readTree(text);
            }
        } catch (IOException e) {
            return null;
        }
    }


    private String getVersion(JsonNode node) {
        if (node == null) {
            return null;
        }

        JsonNode version = node.get("openapi");
        if (version != null) {
            return version.toString();
        }

        version = node.get("io/swagger");
        if (version != null) {
            return version.toString();
        }
        version = node.get("swaggerVersion");
        if (version != null) {
            return version.toString();
        }
        version = node.get("swagger");
        if (version != null) {
            return version.toString();
        }

        LOGGER.debug("version not found!");
        return null;
    }

    public String getResourceFileAsString(String fileName) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(fileName);
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    /**
    *@Description: 将结果写进文件
    *@Param: [fileName] 生成的文件名
    *@return: boolean
    *@Author: zhouxinyu
    *@date: 2020/6/20
    */
    public boolean resultToFile(String fileName){
        Boolean bool = false;
        String filenameTemp = "E:\\test\\resultOpenAPI\\"+fileName+".json";//文件路径+名称+文件类型
        File file = new File(filenameTemp);
        try {
            //如果文件不存在，则创建新的文件
            if(!file.exists()){
                file.createNewFile();

                System.out.println("success create file,the file is "+filenameTemp);
                //创建文件成功后，写入内容到文件里
                ObjectMapper mapper = new ObjectMapper();
                try {
                    mapper.writeValue(file, this.evaluations);
                    bool = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bool;
    }




}
