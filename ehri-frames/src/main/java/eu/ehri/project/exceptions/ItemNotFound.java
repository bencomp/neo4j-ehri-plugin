package eu.ehri.project.exceptions;

public class ItemNotFound extends Exception {
    private static final long serialVersionUID = -3562833443079995695L;

    private String key;
    private String value;
    
    public ItemNotFound(String key, String value) {
        super(String.format("Item with key '%s'='%s' not found", key, value));
        this.key = key;
        this.value = value;
        
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

}
