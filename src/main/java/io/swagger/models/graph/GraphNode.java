package io.swagger.models.graph;

public class GraphNode<T> {
    private String pathName;
    private int subGraphNo;
    private T pathItem;
    public GraphNode(String pathName,T pathItem){
        this.pathName=pathName;
        this.pathItem=pathItem;
        this.subGraphNo=-1;
    }

    public String getPathName() {
        return pathName;
    }

    public void setPathName(String pathName) {
        this.pathName = pathName;
    }

    public int getSubGraphNo() {
        return subGraphNo;
    }

    public void setSubGraphNo(int subGraphNo) {
        this.subGraphNo = subGraphNo;
    }

    public T getPathItem() {
        return pathItem;
    }

    public void setPathItem(T pathItem) {
        this.pathItem = pathItem;
    }
}
