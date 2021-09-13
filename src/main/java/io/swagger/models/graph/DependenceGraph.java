package io.swagger.models.graph;

import io.swagger.v3.oas.models.PathItem;

import javax.ws.rs.core.Link;
import java.util.*;

public class DependenceGraph {
    private Map<String, GraphNode> nodes;// 点集
    private LinkedList<String[]> edges;// 边集 [from,to,weight]
    private Map<String,Set<String>> edgesMap;// 边集，邻接表 <from,[to1,to2,...]>
    private LinkedList<String[]> tempEdges;// 边集操作中间结果
    private Map<Integer, Set<String>> subGs;
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
            edgesMap.get(from).add(to);
        }else {
            Set<String> tos=new HashSet<>();
            tos.add(to);
            edgesMap.put(from,tos);
        }
    }

    /**
     * 添加一个结点（路径）
     * @param pathName
     * @param graphNode
     */
    public void addNode(String pathName,GraphNode graphNode){
        nodes.put(pathName,graphNode);
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

    /**
     * 添加一个结点到子图中
     * @param no
     * @param pathName
     */
    public void addSubG(int no,String pathName){
        if(subGs.containsKey(no)){
            subGs.get(no).add(pathName);
        }else{
            Set<String> temp=new HashSet<>();
            temp.add(pathName);
            subGs.put(no,temp);
        }
    }

    /**
     * 合并子图，大号合并到小号里
     * @param small
     * @param big
     */
    public void unionSubG(int small,int big){
// TODO: 2021/9/13
    }

    /**
     * 返回指定边的所有to边，包括间接的祖宗
     * @param from
     * @return
     */
    public Set<String> allToNode(String from){
        Set<String > ans=new HashSet<>();
        Deque<String> queue=new LinkedList<>();
        for(String to:edgesMap.get(from)){
            queue.addLast(to);
        }
        while(!queue.isEmpty()){
            String temp=queue.removeFirst();
            ans.add(temp);
            if(edgesMap.containsKey(temp)){
                for(String to:edgesMap.get(temp)){
                    queue.addLast(to);
                }
            }
        }
        return ans;
    }

    public Map<String, GraphNode> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, GraphNode> nodes) {
        this.nodes = nodes;
    }

    public LinkedList<String[]> getEdges() {
        return edges;
    }

    public void setEdges(LinkedList<String[]> edges) {
        this.edges = edges;
    }
}
