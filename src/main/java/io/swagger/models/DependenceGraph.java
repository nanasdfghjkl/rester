package io.swagger.models;

import io.swagger.v3.oas.models.PathItem;

import javax.ws.rs.core.Link;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DependenceGraph {
    private Map<String, PathItem> nodes;// 点集
    private LinkedList<String[]> edges;// 边集 [from,to,weight]
    private Map<String,List<String[]>> edgesMap;// 边集，邻接表
    private LinkedList<String[]> tempEdges;// 边集操作中间结果
    public DependenceGraph(){
        nodes=new HashMap<>();
        edges=new LinkedList<>();
        tempEdges=new LinkedList<>();
        edgesMap=new HashMap<>();
    }

    /**
     * 添加1条边
     * @param from
     * @param to
     */
    public void addEdge(String from,String to, String weight){
        String[] edge=new String[]{from,to,weight};
        edges.add(edge);
        tempEdges.add(edge);
        if(edgesMap.containsKey(from)){
            edgesMap.get(from).add(edge);
        }else {
            List<String[]> edges=new LinkedList<>();
            edges.add(edge);
            edgesMap.put(from,edges);
        }
    }

    /**
     * 添加一个结点（路径）
     * @param pathName
     * @param pathItem
     */
    public void addNode(String pathName,PathItem pathItem){
        nodes.put(pathName,pathItem);
    }

    /**
     * node是否存在
     * @param key
     * @return
     */
    public boolean containsNode(String key){
        return nodes.containsKey(key);
    }

    /**
     *node key出度是否为0，即是否存在以结点key为起点的边
     * @param key
     * @return
     */
    public boolean isNodeWithFromEdge(String key){
        return edgesMap.containsKey(key);
    }
    public Map<String, PathItem> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, PathItem> nodes) {
        this.nodes = nodes;
    }

    public LinkedList<String[]> getEdges() {
        return edges;
    }

    public void setEdges(LinkedList<String[]> edges) {
        this.edges = edges;
    }
}
