package org.example.Lab2;

public class SearchResult {

    private String substring;
    private int startIndex;
    private int length;
    private String type;
    private String location;

    public SearchResult(String substring, int startIndex, int length, String type, String location) {
        this.substring = substring;
        this.startIndex = startIndex;
        this.length = length;
        this.type = type;
        this.location = location;
    }

    public String getSubstring() {
        return substring;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getLength() {
        return length;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public void setSubstring(String substring) {
        this.substring = substring;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
