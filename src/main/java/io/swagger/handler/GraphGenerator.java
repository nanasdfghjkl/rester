package io.swagger.handler;

import io.swagger.models.graph.DependenceGraph;

import java.util.*;

public class GraphGenerator {
    private List<String[]> graph;
    public static Random random=new Random();
    public GraphGenerator(List<String[]> graph){
        this.graph=graph;
    }

    /**
     * 删除指定百分比的边
     * @param rate
     * @return
     */
    List<String[]> edgeDrop(int rate){
        int index= (100-rate)*graph.size()/100;
        Collections.shuffle(graph);//乱序
        return graph.subList(0,index);//子集
    }

    /**
     * 逆转边集指定百分比的边
     * @param rate
     * @return
     */
    List<String[]> edgeReverse(int rate){
        int index=rate/100*graph.size();
        if(index==0){
            index=1;
        }
        Collections.shuffle(graph);//乱序
        List<String[]> ans=new LinkedList<>();
        for(int i=0;i<index;i++){
            //逆转边的方向
            String[] reverseEdge=new String[]{graph.get(i)[1],graph.get(i)[0]};
            ans.add(reverseEdge);
        }
        for(int i=index;i<graph.size();i++){
            String[] edgeTemp=new String[]{graph.get(i)[0],graph.get(i)[1]};
            ans.add(edgeTemp);
        }
        return ans;

    }

    /**
     * 将边集列表转换为边集邻接表
     * @param edgesList
     * @return
     */
    Map<String, Set<String>> edgeListToMap(List<String[]> edgesList){
        Map<String,Set<String>> edgesMap=new HashMap<>();
        for(String[] edge:edgesList){
            if(edgesMap.containsKey(edge[0])){
                edgesMap.get(edge[0]).add(edge[1]);
            }else {
                Set<String> tos=new HashSet<>();
                tos.add(edge[1]);
                edgesMap.put(edge[0],tos);
            }
        }
        return edgesMap;
    }
}
